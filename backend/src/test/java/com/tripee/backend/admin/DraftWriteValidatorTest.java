package com.tripee.backend.admin;

import com.tripee.backend.service.admin.DraftWriteValidator;
import com.tripee.backend.common.exception.ApiException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("service")
class DraftWriteValidatorTest {
  private final JsonMapper json = new JsonMapper();
  private final DraftWriteValidator validator = new DraftWriteValidator();

  private ObjectNode draft() {
    return (ObjectNode) json.readTree("""
        {"nameKo":null,"nameEn":null,"coordinates":null,"tagline":null,
         "tagIds":[],"photos":[],"contentItems":[],"safetyDraft":null}
        """);
  }

  @ParameterizedTest
  @ValueSource(strings = {"UNVERIFIED", "PARTIAL", "CHECKED"})
  void validatesStandaloneSafetyWithoutSyntheticDraftFields(String state) {
    var safety = json.createObjectNode().put("reviewState", state);
    safety.putArray("items");
    assertThatCode(() -> validator.validateSafety(safety)).doesNotThrowAnyException();
    var combined = draft();
    combined.set("safetyDraft", safety);
    assertThatCode(() -> validator.validate(combined)).doesNotThrowAnyException();
    safety.put("unexpected", true);
    assertThatThrownBy(() -> validator.validateSafety(safety)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> validator.validate(combined)).isInstanceOf(ApiException.class);
  }

  @Test
  void rejectsMissingStandaloneSafetyButAllowsUnmodifiedSafetyInDraft() {
    assertThatThrownBy(() -> validator.validateSafety(null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> validator.validateSafety(json.nullNode())).isInstanceOf(ApiException.class);
    assertThatCode(() -> validator.validate(draft())).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @CsvSource({"-90,-180", "90,180", "0,0"})
  void acceptsCoordinateBoundaries(double latitude, double longitude) {
    var input = draft();
    input.putObject("coordinates").put("latitude", latitude).put("longitude", longitude);
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @CsvSource({"-90.000001,0", "90.000001,0", "0,-180.000001", "0,180.000001"})
  void rejectsCoordinatesOutsideBoundaries(double latitude, double longitude) {
    var input = draft();
    input.putObject("coordinates").put("latitude", latitude).put("longitude", longitude);
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
  }

  @Test
  void acceptsBigintMaximumAndRejectsOverflowDuplicateAndZero() {
    var input = draft();
    input.putArray("tagIds").add("9223372036854775807");
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    input.putArray("tagIds").add("9223372036854775808");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    input.putArray("tagIds").add("1").add("1");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    input.putArray("tagIds").add("0");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
  }

  @Test
  void checksUnicodeCharacterLimitAndRequiredArrays() {
    var input = draft();
    input.put("nameKo", "가".repeat(150));
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    input.put("nameKo", "가".repeat(151));
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    ObjectNode missing = draft();
    missing.remove("photos");
    assertThatThrownBy(() -> validator.validate(missing)).isInstanceOf(ApiException.class);
  }

  @Test
  void rejectsInvalidSafetyPeriodAndUnconfirmedEvidence() {
    var input = draft();
    input.set("safetyDraft", json.readTree("""
        {"reviewState":"PARTIAL","items":[{"kind":"LOCAL_CAUTION","assessment":"CAUTION",
        "officialLevel":null,"scopeType":null,"originalScopeText":null,"cityCoverage":null,
        "cityScopeNote":null,"title":null,"body":null,"displayOrder":1,
        "effectiveFrom":"2026-01-01T00:00:00Z","effectiveUntil":"2026-01-01T00:00:00Z",
        "sources":[]}]}
        """));
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "-1", "01", "1.0", "9223372036854775808"})
  void rejectsMalformedTagIds(String id) {
    var input = draft();
    input.putArray("tagIds").add(id);
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
  }

  @Test
  void checksDuplicatePhotoIdsAndDisplayOrder() {
    var input = draft();
    var photos = input.putArray("photos");
    photos.add(json.readTree("{\"photoAssetId\":\"1\",\"displayOrder\":1,\"altText\":null,\"caption\":null}"));
    photos.add(json.readTree("{\"photoAssetId\":\"2\",\"displayOrder\":2,\"altText\":null,\"caption\":null}"));
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    ((ObjectNode) photos.get(1)).put("displayOrder", 1);
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    ((ObjectNode) photos.get(1)).put("displayOrder", 2).put("photoAssetId", "1");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    ((ObjectNode) photos.get(1)).put("photoAssetId", "2").put("displayOrder", 0);
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
  }

  @Test
  void evidenceMustBeConfirmedWithNotesAndCannotRepeatSources() {
    var input = draft();
    input.putArray("contentItems").add(json.readTree("""
        {"id":null,"kind":"EXPERIENCE","title":"제목","body":"내용","displayOrder":1,
         "isSelected":true,"sources":[{"sourceDocumentId":"1","evidenceNote":"확인","reviewAction":"CONFIRM"}]}
        """));
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    var item = (ObjectNode) input.get("contentItems").get(0);
    var source = (ObjectNode) item.get("sources").get(0);
    source.putNull("evidenceNote");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    source.put("reviewAction", "KEEP");
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    ((tools.jackson.databind.node.ArrayNode) item.get("sources")).add(source.deepCopy());
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
  }

  @ParameterizedTest
  @CsvSource({"OFFICIAL_ADVISORY,ISSUED,LEVEL_1,true", "OFFICIAL_ADVISORY,NOT_ISSUED,NONE,true",
      "LOCAL_CAUTION,CAUTION,NONE,true", "OFFICIAL_ADVISORY,ISSUED,NONE,false",
      "OFFICIAL_ADVISORY,NOT_ISSUED,LEVEL_1,false", "LOCAL_CAUTION,ISSUED,LEVEL_1,false"})
  void validatesSafetyKindAssessmentAndOfficialLevel(String kind, String assessment, String level, boolean valid) {
    var input = draft();
    var safety = (ObjectNode) json.readTree("""
        {"reviewState":"PARTIAL","items":[{"kind":"LOCAL_CAUTION","assessment":"CAUTION",
        "officialLevel":null,"scopeType":null,"originalScopeText":null,"cityCoverage":null,
        "cityScopeNote":null,"title":null,"body":null,"displayOrder":1,
        "effectiveFrom":null,"effectiveUntil":null,"sources":[]}]}
        """);
    var item = (ObjectNode) safety.get("items").get(0);
    item.put("kind", kind).put("assessment", assessment);
    if (!level.equals("NONE")) item.put("officialLevel", level);
    input.set("safetyDraft", safety);
    if (valid) assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    else assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
  }

  @Test
  void rejectsMalformedCoordinatesAndUnexpectedFields() {
    var input = draft();
    input.putObject("coordinates").put("latitude", 1);
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    input.putObject("coordinates").put("latitude", "35").put("longitude", 135);
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    input.putNull("coordinates").put("unexpected", true);
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    input.remove("unexpected");
    input.put("tagline", "가".repeat(200));
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    input.put("tagline", "가".repeat(201));
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
  }

  @Test
  void checksContentKindSelectionOrderAndIdentifiers() {
    var input = draft();
    var items = input.putArray("contentItems");
    items.add(json.readTree("""
        {"id":"1","kind":"EXPERIENCE","title":"제목","body":"본문","displayOrder":1,"isSelected":true,"sources":[]}
        """));
    var item = (ObjectNode) items.get(0);
    item.put("kind", "INVALID");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    item.put("kind", "EXPERIENCE").put("isSelected", "true");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    item.put("isSelected", true);
    items.add(item.deepCopy());
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    ((ObjectNode) items.get(1)).put("id", "2");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    ((ObjectNode) items.get(1)).put("displayOrder", 2);
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
  }

  @Test
  void validatesSafetyDatesScopeAndEvidence() {
    var input = draft();
    var safety = json.readTree("""
        {"reviewState":"PARTIAL","items":[{"kind":"LOCAL_CAUTION","assessment":"CAUTION",
        "officialLevel":null,"scopeType":"CITY","originalScopeText":"교토","cityCoverage":"WHOLE_CITY",
        "cityScopeNote":"전역","title":"제목","body":"내용","displayOrder":1,
        "effectiveFrom":"2026-01-01T00:00:00Z","effectiveUntil":"2026-01-02T00:00:00Z",
        "sources":[{"sourceDocumentId":"1","evidenceNote":"확인","reviewAction":"CONFIRM"}]}]}
        """);
    input.set("safetyDraft", safety);
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    var item = (ObjectNode) safety.get("items").get(0);
    item.put("scopeType", "INVALID");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    item.put("scopeType", "CITY").put("cityCoverage", "INVALID");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    item.put("cityCoverage", "WHOLE_CITY").put("effectiveFrom", "invalid-date");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    item.put("effectiveFrom", "2026-01-03T00:00:00Z");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    item.putNull("effectiveFrom").putNull("effectiveUntil");
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    ((ObjectNode) safety).put("reviewState", "UNKNOWN");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"tagIds", "photos", "contentItems"})
  void rejectsNullRequiredCollections(String field) {
    var input = draft();
    input.putNull(field);
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOfSatisfying(ApiException.class,
        error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("VALIDATION_FAILED"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"nameKo", "nameEn", "tagline"})
  void acceptsNullOptionalTextButRejectsBlankAndNonString(String field) {
    var input = draft();
    input.putNull(field);
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    input.put(field, " ");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    input.put(field, 123);
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
  }

  @Test
  void requiresBothCoordinatesWhenCoordinateObjectIsPresent() {
    var input = draft();
    input.putObject("coordinates").putNull("latitude").put("longitude", 135);
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    input.putObject("coordinates").put("latitude", 35).putNull("longitude");
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    input.putNull("coordinates");
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
  }

  @Test
  void validatesPhotoOrderIntegerRangeAndOptionalTextBoundaries() {
    var input = draft();
    var photo = input.putArray("photos").addObject().put("photoAssetId", "1").put("displayOrder", 1)
        .putNull("altText").putNull("caption");
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    photo.put("displayOrder", 1.5);
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    photo.put("displayOrder", 2147483648L);
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    photo.put("displayOrder", 1).put("altText", "가".repeat(501));
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    photo.put("altText", "가".repeat(500)).put("caption", "나".repeat(1001));
    assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(ApiException.class);
    photo.put("caption", "나".repeat(1000));
    assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
  }
}
