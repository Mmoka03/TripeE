package com.tripee.backend.service.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class PublicationValidator {
  public List<Violation> validate(JsonNode draft, JsonNode safety) {
    List<Violation> errors = new ArrayList<>();
    for (String field : List.of("nameKo", "nameEn", "coordinates", "tagline")) {
      if (draft.get(field).isNull()) add(errors, "/" + field, "REQUIRED", "게시할 필수 정보입니다.");
    }
    if (draft.get("photos").isEmpty()) add(errors, "/photos", "REQUIRED", "사진이 필요합니다.");
    int index = 0;
    for (JsonNode photo : draft.get("photos")) {
      if (photo.get("altText").isNull()) add(errors, "/photos/" + index + "/altText", "REQUIRED", "대체 텍스트가 필요합니다.");
      if (photo.get("asset").get("rightsCheckedAt").isNull()) add(errors, "/photos/" + index, "RIGHTS_UNCHECKED", "사진 권리를 확인해 주세요.");
      index++;
    }
    Set<String> required = new java.util.HashSet<>(Set.of("EXPERIENCE", "VISIT_PERIOD", "ARRIVAL", "LOCAL_TRANSPORT"));
    index = 0;
    for (JsonNode item : draft.get("contentItems")) {
      if (item.get("isSelected").asBoolean()) {
        required.remove(item.get("kind").asString());
        if (item.get("title").isNull() || item.get("body").isNull())
          add(errors, "/contentItems/" + index, "REQUIRED", "선정한 콘텐츠의 제목과 본문을 입력해 주세요.");
        boolean verified = false;
        for (JsonNode source : item.get("sources")) {
          if (!source.get("checkedAt").isNull() && !source.get("evidenceNote").isNull()) verified = true;
        }
        if (!verified) add(errors, "/contentItems/" + index + "/sources", "SOURCE_UNCHECKED", "검토한 출처가 필요합니다.");
      }
      index++;
    }
    for (String kind : required) add(errors, "/contentItems", "REQUIRED_" + kind, "필수 콘텐츠가 필요합니다.");
    errors.addAll(validateSafety(safety));
    return errors;
  }

  public List<Violation> validateSafety(JsonNode safety) {
    List<Violation> errors = new ArrayList<>();
    int index;
    String state = safety.get("reviewState").asString();
    JsonNode items = safety.get("items");
    if ("UNVERIFIED".equals(state) && !items.isEmpty())
      add(errors, "/safetyDraft/items", "SAFETY_UNVERIFIED", "안전정보를 검토해 주세요.");
    if ("PARTIAL".equals(state) && items.isEmpty())
      add(errors, "/safetyDraft/items", "REQUIRED", "검토한 안전정보가 필요합니다.");
    boolean official = false;
    index = 0;
    for (JsonNode item : items) {
      if ("OFFICIAL_ADVISORY".equals(item.get("kind").asString())) official = true;
      for (String field : List.of("title", "body", "scopeType", "originalScopeText", "cityCoverage", "cityScopeNote")) {
        if (item.get(field).isNull()) add(errors, "/safetyDraft/items/" + index + "/" + field, "REQUIRED", "안전정보를 입력해 주세요.");
      }
      boolean verified = false;
      for (JsonNode source : item.get("sources")) {
        if (!source.get("checkedAt").isNull() && !source.get("evidenceNote").isNull()) verified = true;
      }
      if (!verified) add(errors, "/safetyDraft/items/" + index + "/sources", "SOURCE_UNCHECKED", "검토한 출처가 필요합니다.");
      index++;
    }
    if ("CHECKED".equals(state) && !official)
      add(errors, "/safetyDraft/items", "OFFICIAL_REQUIRED", "공식 안전정보 확인이 필요합니다.");
    return errors;
  }

  private static void add(List<Violation> errors, String path, String code, String message) {
    errors.add(new Violation(path, code, message));
  }

  public record Violation(String path, String code, String message) {}
}
