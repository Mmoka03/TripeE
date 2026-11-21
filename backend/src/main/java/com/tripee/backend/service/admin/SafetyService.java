package com.tripee.backend.service.admin;

import com.tripee.backend.repository.admin.SafetyRepository;
import com.tripee.backend.repository.admin.DraftRepository;
import com.tripee.backend.repository.admin.EditLockRepository;
import com.tripee.backend.common.exception.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import com.tripee.backend.model.admin.Versioned;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;

@Service
@Profile("postgres")
public class SafetyService {
  private final SafetyRepository safety;
  private final DraftRepository drafts;
  private final EditLockRepository cityLocks;
  private final DraftWriteValidator validator;
  private final PublicationValidator publication;
  private final ObjectMapper json;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public SafetyService(SafetyRepository safety, DraftRepository drafts, EditLockRepository cityLocks,
                       DraftWriteValidator validator, PublicationValidator publication, ObjectMapper json,
                       TransactionTemplate transactions, Clock clock) {
    this.safety = safety;
    this.drafts = drafts;
    this.cityLocks = cityLocks;
    this.validator = validator;
    this.publication = publication;
    this.json = json;
    this.transactions = transactions;
    this.clock = clock;
  }

  public Versioned<JsonNode> current(long cityId) {
    JsonNode result = safety.current(cityId).orElseThrow(() ->
        new ApiException(HttpStatus.NOT_FOUND, "CITY_NOT_FOUND", "도시를 찾을 수 없어요."));
    String revision = result.get("revisionId").isNull() ? "empty" : result.get("revisionId").asString();
    return new Versioned<>(result, "\"safety-" + cityId + "-" + revision + "\"");
  }

  public Versioned<JsonNode> publish(long cityId, long actorId, String ifMatch,
                                           JsonNode input, HttpSession session) {
    synchronized (session) {
      return transactions.execute(status -> {
        cityLocks.lockCity(cityId);
        Object state = session.getAttribute(AdminSession.ATTRIBUTE);
        if (!(state instanceof AdminSession admin) || admin.expired(clock.instant())) {
          throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "세션이 만료되었습니다.");
        }
        var beforeResponse = current(cityId);
        if (ifMatch == null) throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", "If-Match가 필요합니다.");
        if (!ifMatch.matches("\"[^\"\\r\\n]+\"")) {
          throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "If-Match 형식을 확인해 주세요.");
        }
        if (!ifMatch.equals(beforeResponse.version())) {
          throw new ApiException(HttpStatus.PRECONDITION_FAILED, "SAFETY_VERSION_MISMATCH", "안전정보가 변경되었습니다.");
        }
        if (input == null || input.isNull()) {
          throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "안전정보를 입력해 주세요.");
        }
        var wrapper = json.createObjectNode();
        wrapper.putNull("nameKo"); wrapper.putNull("nameEn"); wrapper.putNull("coordinates"); wrapper.putNull("tagline");
        wrapper.putArray("tagIds"); wrapper.putArray("photos"); wrapper.putArray("contentItems");
        wrapper.set("safetyDraft", input);
        validator.validate(wrapper);
        JsonNode before = beforeResponse.value();
        Long previous = before.get("revisionId").isNull() ? null : Long.parseLong(before.get("revisionId").asString());
        long newId = drafts.createPublishedSafety(cityId, actorId, previous, input);
        var afterResponse = current(cityId);
        JsonNode after = afterResponse.value();
        if (!publication.validateSafety(after).isEmpty()) {
          throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "안전정보 게시 조건을 확인해 주세요.");
        }
        drafts.auditSafety(cityId, actorId, newId, before, after);
        return afterResponse;
      });
    }
  }
}
