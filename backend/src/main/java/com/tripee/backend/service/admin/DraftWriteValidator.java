package com.tripee.backend.service.admin;

import com.tripee.backend.common.exception.ApiException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class DraftWriteValidator {
  private static final Set<String> DRAFT_FIELDS =
      Set.of("nameKo", "nameEn", "coordinates", "tagline", "tagIds", "photos", "contentItems", "safetyDraft");
  private static final Set<String> CONTENT_KINDS =
      Set.of("EXPERIENCE", "CONSIDERATION", "VISIT_PERIOD", "ARRIVAL", "LOCAL_TRANSPORT");

  public void validate(JsonNode draft) {
    objectFields(draft, DRAFT_FIELDS);
    nullableText(draft.get("nameKo"), 150);
    nullableText(draft.get("nameEn"), 150);
    nullableText(draft.get("tagline"), 200);
    coordinates(draft.get("coordinates"));
    JsonNode tags = array(draft.get("tagIds"));
    Set<Long> tagIds = new HashSet<>();
    for (JsonNode tag : tags) {
      if (!tagIds.add(id(tag))) invalid();
    }
    JsonNode photos = array(draft.get("photos"));
    Set<Long> assets = new HashSet<>();
    Set<Integer> photoOrder = new HashSet<>();
    for (JsonNode photo : photos) {
      objectFields(photo, Set.of("photoAssetId", "displayOrder", "altText", "caption"));
      if (!assets.add(id(photo.get("photoAssetId"))) || !photoOrder.add(positiveInt(photo.get("displayOrder")))) invalid();
      nullableText(photo.get("altText"), 500);
      nullableText(photo.get("caption"), 1000);
    }
    JsonNode items = array(draft.get("contentItems"));
    Set<Long> itemIds = new HashSet<>();
    Set<String> itemOrder = new HashSet<>();
    for (JsonNode item : items) {
      objectFields(item, Set.of("id", "kind", "title", "body", "displayOrder", "isSelected", "sources"));
      if (!item.get("id").isNull() && !itemIds.add(id(item.get("id")))) invalid();
      String kind = text(item.get("kind"));
      if (!CONTENT_KINDS.contains(kind) || !item.get("isSelected").isBoolean()) invalid();
      if (!itemOrder.add(kind + ":" + positiveInt(item.get("displayOrder")))) invalid();
      nullableText(item.get("title"), 200);
      nullableText(item.get("body"), 3000);
      sources(item.get("sources"));
    }
    JsonNode safety = draft.get("safetyDraft");
    if (!safety.isNull()) validateSafety(safety);
  }

  // 일반 초안 저장과 안전정보 독립 게시에서 같은 입력 규칙을 사용함.
  public void validateSafety(JsonNode safety) {
    objectFields(safety, Set.of("reviewState", "items"));
    if (!Set.of("UNVERIFIED", "PARTIAL", "CHECKED").contains(text(safety.get("reviewState")))) invalid();
    Set<Integer> safetyOrder = new HashSet<>();
    for (JsonNode item : array(safety.get("items"))) {
      objectFields(item, Set.of("kind", "assessment", "officialLevel", "scopeType", "originalScopeText",
          "cityCoverage", "cityScopeNote", "title", "body", "displayOrder", "effectiveFrom", "effectiveUntil", "sources"));
      String kind = text(item.get("kind"));
      String assessment = text(item.get("assessment"));
      String level = item.get("officialLevel").isNull() ? null : text(item.get("officialLevel"));
      if (!safetyOrder.add(positiveInt(item.get("displayOrder")))) invalid();
      if (!(kind.equals("OFFICIAL_ADVISORY") && assessment.equals("ISSUED")
                && level != null && Set.of("LEVEL_1", "LEVEL_2", "LEVEL_3", "LEVEL_4", "SPECIAL").contains(level))
          && !(kind.equals("OFFICIAL_ADVISORY") && assessment.equals("NOT_ISSUED") && level == null)
          && !(kind.equals("LOCAL_CAUTION") && assessment.equals("CAUTION") && level == null)) invalid();
      nullableEnum(item.get("scopeType"), Set.of("COUNTRY_WIDE", "REGION", "CITY", "SPECIFIC_PLACE"));
      nullableEnum(item.get("cityCoverage"), Set.of("WHOLE_CITY", "PART_OF_CITY"));
      nullableText(item.get("originalScopeText"), 4000);
      nullableText(item.get("cityScopeNote"), 4000);
      nullableText(item.get("title"), 200);
      nullableText(item.get("body"), 3000);
      var from = instant(item.get("effectiveFrom"));
      var until = instant(item.get("effectiveUntil"));
      if (from != null && until != null && !until.isAfter(from)) invalid();
      sources(item.get("sources"));
    }
  }

  private static void sources(JsonNode sourceList) {
    Set<Long> ids = new HashSet<>();
    for (JsonNode source : array(sourceList)) {
      objectFields(source, Set.of("sourceDocumentId", "evidenceNote", "reviewAction"));
      if (!ids.add(id(source.get("sourceDocumentId")))) invalid();
      nullableText(source.get("evidenceNote"), 4000);
      String action = text(source.get("reviewAction"));
      if (!Set.of("KEEP", "CONFIRM").contains(action)
          || (action.equals("CONFIRM") && source.get("evidenceNote").isNull())) invalid();
    }
  }

  private static void coordinates(JsonNode coordinates) {
    if (coordinates.isNull()) return;
    objectFields(coordinates, Set.of("latitude", "longitude"));
    if (!coordinates.get("latitude").isNumber() || !coordinates.get("longitude").isNumber()) invalid();
    var lat = coordinates.get("latitude").decimalValue();
    var lon = coordinates.get("longitude").decimalValue();
    if (lat.compareTo(java.math.BigDecimal.valueOf(-90)) < 0 || lat.compareTo(java.math.BigDecimal.valueOf(90)) > 0
        || lon.compareTo(java.math.BigDecimal.valueOf(-180)) < 0 || lon.compareTo(java.math.BigDecimal.valueOf(180)) > 0) invalid();
  }

  private static JsonNode array(JsonNode value) {
    if (!value.isArray()) invalid();
    return value;
  }

  private static void objectFields(JsonNode value, Set<String> fields) {
    if (value == null || !value.isObject() || value.size() != fields.size() || fields.stream().anyMatch(field -> !value.has(field))) invalid();
  }

  private static long id(JsonNode value) {
    String raw = text(value);
    try {
      if (!raw.matches("[1-9][0-9]{0,18}")) invalid();
      return Long.parseLong(raw);
    } catch (NumberFormatException invalidId) {
      invalid();
      return 0;
    }
  }

  private static int positiveInt(JsonNode value) {
    if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 1) invalid();
    return value.asInt();
  }

  private static String text(JsonNode value) {
    if (!value.isString()) invalid();
    return value.asString();
  }

  private static void nullableText(JsonNode value, int max) {
    if (value.isNull()) return;
    String text = text(value);
    if (text.isBlank() || text.codePointCount(0, text.length()) > max) invalid();
  }

  private static void nullableEnum(JsonNode value, Set<String> allowed) {
    if (!value.isNull() && !allowed.contains(text(value))) invalid();
  }

  private static java.time.Instant instant(JsonNode value) {
    if (value.isNull()) return null;
    try {
      return java.time.OffsetDateTime.parse(text(value)).toInstant();
    } catch (java.time.format.DateTimeParseException invalidTime) {
      invalid();
      return null;
    }
  }

  private static void invalid() {
    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "초안 입력을 확인해 주세요.");
  }
}
