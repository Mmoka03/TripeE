package com.tripee.backend.service.admin;

import com.tripee.backend.repository.admin.DraftRepository;
import com.tripee.backend.repository.admin.SourceRepository;
import com.tripee.backend.common.exception.ApiException;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
@Profile("postgres")
public class ExperienceSuggestionService {
  private final DraftRepository drafts;
  private final SourceRepository sources;
  private final EditLockService locks;
  private final ExperienceSuggestionProvider provider;
  private final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final java.time.Duration timeout;

  @org.springframework.beans.factory.annotation.Autowired
  public ExperienceSuggestionService(DraftRepository drafts, SourceRepository sources,
      EditLockService locks, ExperienceSuggestionProvider provider) {
    this(drafts, sources, locks, provider, java.time.Duration.ofSeconds(30));
  }

  public ExperienceSuggestionService(DraftRepository drafts, SourceRepository sources,
      EditLockService locks, ExperienceSuggestionProvider provider, java.time.Duration timeout) {
    this.drafts = drafts;
    this.sources = sources;
    this.locks = locks;
    this.provider = provider;
    this.timeout = timeout;
  }

  public Response suggest(long cityId, long actorId, String token, HttpSession session, JsonNode request) {
    Input input = validateInput(request);
    locks.withLease(cityId, actorId, token, session, () -> {
      JsonNode draft = drafts.find(cityId).orElseThrow(() ->
          new ApiException(HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", "초안을 찾을 수 없어요."));
      if (!draft.get("revisionId").asString().equals(input.revisionId())
          || !draft.get("editVersion").asString().equals(input.editVersion())) {
        throw new ApiException(HttpStatus.CONFLICT, "DRAFT_VERSION_MISMATCH", "초안이 변경되었습니다.");
      }
      for (SourceInput source : input.sources()) {
        if (sources.find(Long.parseLong(source.sourceDocumentId())).isEmpty()) throw invalid();
      }
      return null;
    });
    // The transaction and city-row lock have ended before any external AI call starts.
    var future = executor.submit(() -> provider.generate(input.sources()));
    JsonNode result;
    try {
      result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException timeout) {
      future.cancel(true);
      throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "AI 응답 시간이 초과되었습니다.");
    } catch (InterruptedException interrupted) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw upstream();
    } catch (ExecutionException failure) {
      if (failure.getCause() instanceof ApiException api && "AI_UPSTREAM_ERROR".equals(api.code())) throw api;
      throw upstream();
    }
    return validateOutput(input, result);
  }

  private Input validateInput(JsonNode request) {
    if (!fields(request, Set.of("expectedRevisionId", "expectedEditVersion", "sources"))) throw malformed();
    String revision = numeric(request.get("expectedRevisionId"), false);
    String version = numeric(request.get("expectedEditVersion"), true);
    JsonNode entries = request.get("sources");
    if (!entries.isArray() || entries.size() < 1 || entries.size() > 5) throw invalid();
    List<SourceInput> result = new ArrayList<>();
    int total = 0;
    for (JsonNode entry : entries) {
      if (!fields(entry, Set.of("sourceDocumentId", "text"))) throw malformed();
      String id = numeric(entry.get("sourceDocumentId"), false);
      JsonNode text = entry.get("text");
      if (!text.isString() || text.asString().isBlank()) throw invalid();
      int length = text.asString().codePointCount(0, text.asString().length());
      if (length > 10_000) throw invalid();
      total += length;
      result.add(new SourceInput(id, text.asString()));
    }
    if (total > 30_000) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "자료의 전체 길이를 확인해 주세요.");
    return new Input(revision, version, List.copyOf(result));
  }

  private Response validateOutput(Input input, JsonNode result) {
    if (!fields(result, Set.of("experiences", "warnings")) || !result.get("experiences").isArray()
        || result.get("experiences").size() > 5 || !result.get("warnings").isArray()) throw invalidOutput();
    Set<String> allowed = new HashSet<>();
    input.sources().forEach(s -> allowed.add(s.sourceDocumentId()));
    List<Experience> experiences = new ArrayList<>();
    for (JsonNode item : result.get("experiences")) {
      if (!fields(item, Set.of("title", "body", "sourceDocumentIds"))
          || !validText(item.get("title"), 200) || !validText(item.get("body"), 3000)
          || !item.get("sourceDocumentIds").isArray() || item.get("sourceDocumentIds").isEmpty()) throw invalidOutput();
      List<String> ids = new ArrayList<>();
      for (JsonNode id : item.get("sourceDocumentIds")) {
        if (!id.isString() || !allowed.contains(id.asString()) || ids.contains(id.asString())) throw invalidOutput();
        ids.add(id.asString());
      }
      experiences.add(new Experience(item.get("title").asString(), item.get("body").asString(), List.copyOf(ids)));
    }
    List<String> warnings = new ArrayList<>();
    for (JsonNode warning : result.get("warnings")) {
      if (!warning.isString()) throw invalidOutput();
      warnings.add(warning.asString());
    }
    return new Response(input.revisionId(), input.editVersion(), false, List.copyOf(experiences), List.copyOf(warnings));
  }

  private static boolean fields(JsonNode node, Set<String> fields) {
    return node != null && node.isObject() && node.size() == fields.size() && fields.stream().allMatch(node::has);
  }
  private static boolean validText(JsonNode node, int max) {
    return node != null && node.isString() && !node.asString().isBlank()
        && node.asString().codePointCount(0, node.asString().length()) <= max;
  }
  private static String numeric(JsonNode node, boolean zeroAllowed) {
    if (node == null || !node.isString()) throw malformed();
    String value = node.asString();
    try {
      if (!value.matches(zeroAllowed ? "(0|[1-9][0-9]{0,18})" : "[1-9][0-9]{0,18}")) throw new NumberFormatException();
      Long.parseLong(value);
      return value;
    } catch (NumberFormatException bad) { throw malformed(); }
  }
  private static ApiException malformed() { return new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "요청을 확인해 주세요."); }
  private static ApiException invalid() { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "입력 자료를 확인해 주세요."); }
  private static ApiException invalidOutput() { return new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "AI 응답 형식을 확인할 수 없습니다."); }
  private static ApiException upstream() { return new ApiException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_ERROR", "AI 요청을 처리할 수 없습니다."); }
  @PreDestroy public void shutdown() { executor.shutdownNow(); }
  private record Input(String revisionId, String editVersion, List<SourceInput> sources) {}
  public record SourceInput(String sourceDocumentId, String text) {}
  public record Experience(String title, String body, List<String> sourceDocumentIds) {}
  public record Response(String basedOnRevisionId, String basedOnEditVersion, boolean persisted,
                         List<Experience> experiences, List<String> warnings) {}
}
