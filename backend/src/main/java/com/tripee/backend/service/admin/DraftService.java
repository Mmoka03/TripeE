package com.tripee.backend.service.admin;

import com.tripee.backend.repository.admin.DraftRepository;
import com.tripee.backend.common.exception.ApiException;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import com.tripee.backend.model.admin.Versioned;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import com.tripee.backend.repository.admin.SafetyRepository;
import com.tripee.backend.repository.admin.AdminCityRepository;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("postgres")
public class DraftService {
  private final DraftRepository drafts;
  private final EditLockService locks;
  private final DraftWriteValidator validator;
  private final SafetyRepository safety;
  private final PublicationValidator publicationValidator;
  private final ObjectMapper json;
  private final AdminCityRepository cities;

  public DraftService(DraftRepository drafts, EditLockService locks, DraftWriteValidator validator,
                      SafetyRepository safety, PublicationValidator publicationValidator, ObjectMapper json,
                      AdminCityRepository cities) {
    this.drafts = drafts;
    this.locks = locks;
    this.validator = validator;
    this.safety = safety;
    this.publicationValidator = publicationValidator;
    this.json = json;
    this.cities = cities;
  }

  public Preview preview(long cityId, long actorId, String token, JsonNode expected, HttpSession session) {
    return locks.withLease(cityId, actorId, token, session, () -> evaluate(cityId, expected));
  }

  private Preview evaluate(long cityId, JsonNode expected) {
      JsonNode draft = current(cityId);
      if (!expected.isObject() || expected.size() != 2 || !expected.has("expectedRevisionId")
          || !expected.has("expectedEditVersion") || !expected.get("expectedRevisionId").isString()
          || !expected.get("expectedEditVersion").isString()) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "초안 버전을 확인해 주세요.");
      }
      if (!expected.get("expectedRevisionId").equals(draft.get("revisionId"))
          || !expected.get("expectedEditVersion").equals(draft.get("editVersion"))) {
        throw new ApiException(HttpStatus.CONFLICT, "DRAFT_VERSION_MISMATCH", "초안이 변경되었습니다.");
      }
      JsonNode publishedSafety = safety.current(cityId).orElseThrow();
      JsonNode effective = publishedSafety;
      if (!draft.get("safetyDraft").isNull()) {
        var modified = json.createObjectNode();
        modified.set("revisionId", draft.get("safetyDraft").get("revisionId"));
        modified.set("reviewState", draft.get("safetyDraft").get("reviewState"));
        modified.set("items", draft.get("safetyDraft").get("items"));
        modified.putNull("publishedAt");
        modified.putNull("publishedBy");
        effective = modified;
      }
      var violations = new java.util.ArrayList<>(publicationValidator.validate(draft, effective));
      if (!draft.get("safetyDraft").isNull()
          && !draft.get("baseSafetyRevisionId").equals(publishedSafety.get("revisionId"))) {
        violations.add(new PublicationValidator.Violation("/safetyDraft", "SAFETY_CHANGED", "안전정보를 다시 검토해 주세요."));
      }
      return new Preview(Long.toString(cityId), draft.get("revisionId").asString(),
          draft.get("editVersion").asString(), effective, draft, violations.isEmpty(), violations);
  }

  public Published publish(long cityId, long actorId, String token, JsonNode expected,
                                          HttpSession session) {
    return locks.withLease(cityId, actorId, token, session, () -> {
      Preview preview = evaluate(cityId, expected);
      if (!preview.canPublish()) {
        boolean safetyChanged = preview.violations().stream().anyMatch(v -> v.code().equals("SAFETY_CHANGED"));
        throw new ApiException(safetyChanged ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY,
            safetyChanged ? "SAFETY_CHANGED" : "PUBLICATION_NOT_READY", "게시 조건을 확인해 주세요.");
      }
      long revisionId = drafts.publish(cityId, actorId, preview.draft(), preview.effectiveSafety());
      JsonNode result = json.valueToTree(cities.find(cityId));
      return new Published(result, revisionId);
    });
  }

  public record Published(JsonNode city, long revisionId) {}

  public record Preview(String cityId, String revisionId, String editVersion, JsonNode effectiveSafety,
                        JsonNode draft, boolean canPublish, java.util.List<PublicationValidator.Violation> violations) {}

  public Versioned<JsonNode> reviewSafety(long cityId, long actorId, String token, JsonNode input,
                                               HttpSession session) {
    return locks.withLease(cityId, actorId, token, session, () -> {
      if (!input.isObject() || input.size() != 4 || !input.has("expectedRevisionId")
          || !input.has("expectedEditVersion") || !input.has("reviewedPublicSafetyRevisionId")
          || !input.has("safety")) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "재검토 요청을 확인해 주세요.");
      }
      JsonNode before = current(cityId);
      if (!input.get("expectedRevisionId").equals(before.get("revisionId"))
          || !input.get("expectedEditVersion").equals(before.get("editVersion"))) {
        throw new ApiException(HttpStatus.CONFLICT, "DRAFT_VERSION_MISMATCH", "초안이 변경되었습니다.");
      }
      JsonNode currentSafety = safety.current(cityId).orElseThrow();
      JsonNode reviewed = input.get("reviewedPublicSafetyRevisionId");
      if (!reviewed.equals(currentSafety.get("revisionId"))) {
        throw new ApiException(HttpStatus.CONFLICT, "SAFETY_CHANGED", "공개 안전정보가 변경되었습니다.");
      }
      if (input.get("safety").isNull()) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "안전정보를 입력해 주세요.");
      }
      validator.validateSafety(input.get("safety"));
      Long reviewedId = reviewed.isNull() ? null : Long.parseLong(reviewed.asString());
      drafts.reviewSafety(cityId, actorId, before, input.get("safety"), reviewedId);
      return versioned(current(cityId));
    });
  }

  public Versioned<JsonNode> replace(long cityId, long actorId, String token, String ifMatch,
                                           JsonNode input, HttpSession session) {
    return locks.withLease(cityId, actorId, token, session, () -> {
      JsonNode before = current(cityId);
      checkMatch(before, ifMatch);
      validator.validate(input);
      drafts.replace(cityId, actorId, before, input);
      return versioned(current(cityId));
    });
  }

  public Versioned<JsonNode> find(long cityId, long actorId, String token, HttpSession session) {
    return locks.withLease(cityId, actorId, token, session, () -> {
      return versioned(current(cityId));
    });
  }

  public Versioned<JsonNode> create(long cityId, long actorId, String token, HttpSession session) {
    return locks.withLease(cityId, actorId, token, session, () -> {
      drafts.create(cityId, actorId);
      return versioned(current(cityId));
    });
  }

  public void discard(long cityId, long actorId, String token, String ifMatch, HttpSession session) {
    locks.withLease(cityId, actorId, token, session, () -> {
      JsonNode current = current(cityId);
      checkMatch(current, ifMatch);
      drafts.discard(cityId, actorId, current);
      return null;
    });
  }

  private JsonNode current(long cityId) {
    return drafts.find(cityId).orElseThrow(() ->
        new ApiException(HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", "초안을 찾을 수 없어요."));
  }

  private static String etag(JsonNode draft) {
    return "\"draft-" + draft.get("revisionId").asString() + "-" + draft.get("editVersion").asString() + "\"";
  }

  private static void checkMatch(JsonNode draft, String value) {
    if (value == null) throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", "If-Match가 필요합니다.");
    if (!value.matches("\"[^\"\\r\\n]+\"")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "If-Match 형식을 확인해 주세요.");
    }
    if (!etag(draft).equals(value)) {
      throw new ApiException(HttpStatus.PRECONDITION_FAILED, "RESOURCE_VERSION_MISMATCH", "초안이 변경되었습니다.");
    }
  }

  private static Versioned<JsonNode> versioned(JsonNode draft) {
    return new Versioned<>(draft, etag(draft));
  }
}
