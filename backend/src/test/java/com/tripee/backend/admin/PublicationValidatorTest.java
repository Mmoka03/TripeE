package com.tripee.backend.admin;

import com.tripee.backend.service.admin.PublicationValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("service")
class PublicationValidatorTest {
  private final JsonMapper json = new JsonMapper();
  private final PublicationValidator validator = new PublicationValidator();

  @Test
  void unverifiedSafetyCanBeEmptyButNotContainUnreviewedItems() {
    var empty = json.readTree("{\"reviewState\":\"UNVERIFIED\",\"items\":[]}");
    assertThat(validator.validateSafety(empty)).isEmpty();
    var incomplete = json.readTree("""
        {"reviewState":"UNVERIFIED","items":[{"kind":"LOCAL_CAUTION","title":null,"body":null,
         "scopeType":null,"originalScopeText":null,"cityCoverage":null,"cityScopeNote":null,"sources":[]}]}
        """);
    assertThat(validator.validateSafety(incomplete)).extracting(PublicationValidator.Violation::code)
        .contains("SAFETY_UNVERIFIED", "REQUIRED", "SOURCE_UNCHECKED");
  }

  @Test
  void checkedSafetyRequiresOfficialAdvisoryAndVerifiedSource() {
    var localOnly = json.readTree("""
        {"reviewState":"CHECKED","items":[{"kind":"LOCAL_CAUTION","title":"제목","body":"본문",
         "scopeType":"CITY","originalScopeText":"교토","cityCoverage":"WHOLE_CITY",
         "cityScopeNote":"전역","sources":[{"checkedAt":"2026-01-01T00:00:00Z","evidenceNote":"확인"}]}]}
        """);
    assertThat(validator.validateSafety(localOnly)).extracting(PublicationValidator.Violation::code)
        .containsExactly("OFFICIAL_REQUIRED");
  }

  @Test
  void requiresCoreFieldsPhotosAndAllSelectedContentCategories() {
    var draft = json.readTree("""
        {"nameKo":null,"nameEn":null,"coordinates":null,"tagline":null,"photos":[],"contentItems":[]}
        """);
    var safety = json.readTree("{\"reviewState\":\"UNVERIFIED\",\"items\":[]}");
    assertThat(validator.validate(draft, safety)).extracting(PublicationValidator.Violation::code)
        .contains("REQUIRED", "REQUIRED_EXPERIENCE", "REQUIRED_VISIT_PERIOD",
            "REQUIRED_ARRIVAL", "REQUIRED_LOCAL_TRANSPORT");
    assertThat(validator.validate(draft, safety)).extracting(PublicationValidator.Violation::path)
        .contains("/nameKo", "/nameEn", "/coordinates", "/tagline", "/photos");
  }

  @Test
  void selectedContentAndPhotosNeedConfirmedRightsAndEvidence() {
    var draft = json.readTree("""
        {"nameKo":"교토","nameEn":"Kyoto","coordinates":{},"tagline":"여행",
         "photos":[{"altText":null,"asset":{"rightsCheckedAt":null}}],
         "contentItems":[{"kind":"EXPERIENCE","isSelected":true,"title":null,"body":null,"sources":[]}]}
        """);
    var safety = json.readTree("{\"reviewState\":\"UNVERIFIED\",\"items\":[]}");
    assertThat(validator.validate(draft, safety)).extracting(PublicationValidator.Violation::code)
        .contains("RIGHTS_UNCHECKED", "SOURCE_UNCHECKED", "REQUIRED");
  }

  @Test
  void partialSafetyNeedsAtLeastOneItemAndCheckedSafetyNeedsVerifiedOfficialItem() {
    var partial = json.readTree("{\"reviewState\":\"PARTIAL\",\"items\":[]}");
    assertThat(validator.validateSafety(partial)).extracting(PublicationValidator.Violation::code).contains("REQUIRED");
    var official = json.readTree("""
        {"reviewState":"CHECKED","items":[{"kind":"OFFICIAL_ADVISORY","title":"제목","body":"본문",
         "scopeType":"CITY","originalScopeText":"교토","cityCoverage":"WHOLE_CITY",
         "cityScopeNote":"전역","sources":[{"checkedAt":"2026-01-01T00:00:00Z","evidenceNote":"확인"}]}]}
        """);
    assertThat(validator.validateSafety(official)).isEmpty();
    var source = (tools.jackson.databind.node.ObjectNode) official.get("items").get(0).get("sources").get(0);
    source.putNull("evidenceNote");
    assertThat(validator.validateSafety(official)).extracting(PublicationValidator.Violation::code).contains("SOURCE_UNCHECKED");
    source.put("evidenceNote", "확인").putNull("checkedAt");
    assertThat(validator.validateSafety(official)).extracting(PublicationValidator.Violation::code).contains("SOURCE_UNCHECKED");
  }
}
