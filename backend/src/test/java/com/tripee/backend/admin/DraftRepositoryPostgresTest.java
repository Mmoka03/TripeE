package com.tripee.backend.admin;

import com.tripee.backend.PostgresIntegrationTestBase;
import com.tripee.backend.common.exception.ApiException;
import com.tripee.backend.repository.admin.AdminCityRepository;
import com.tripee.backend.repository.admin.DraftRepository;
import com.tripee.backend.repository.admin.SafetyRepository;
import com.tripee.backend.repository.admin.SourceRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.*;

@Tag("postgres")
@Tag("repository")
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TRIPEE_TEST_ISOLATED_DB", matches = "true")
@Transactional
class DraftRepositoryPostgresTest extends PostgresIntegrationTestBase {
  @Autowired AdminCityRepository cities;
  @Autowired DraftRepository drafts;
  @Autowired SafetyRepository safety;
  @Autowired SourceRepository sources;
  @Autowired ObjectMapper json;

  private ObjectNode safetyInput(String sourceId, String action, String title) {
    return (ObjectNode) json.readTree("""
        {"reviewState":"CHECKED","items":[{"kind":"OFFICIAL_ADVISORY","assessment":"NOT_ISSUED",
         "officialLevel":null,"scopeType":"CITY","originalScopeText":"교토","cityCoverage":"WHOLE_CITY",
         "cityScopeNote":"전역","title":"%s","body":"내용","displayOrder":1,
         "effectiveFrom":null,"effectiveUntil":null,"sources":[{"sourceDocumentId":"%s",
         "evidenceNote":"근거","reviewAction":"%s"}]}]}
        """.formatted(title, sourceId, action));
  }

  private String checkedAt(tools.jackson.databind.JsonNode data) {
    return data.at("/items/0/sources/0/checkedAt").asString();
  }

  @Test
  void safetyPublicationPreservesOnlyMatchingReviewedEvidence() {
    long city = cities.create(1, 1, null, null, null, null, null)[0];
    String source = sources.create("https://example.org/advisory", "공식 출처", null).id();
    long first = drafts.createPublishedSafety(city, 1, null, safetyInput(source, "CONFIRM", "기존"));
    var firstView = safety.current(city).orElseThrow();
    assertThat(firstView.at("/items/0/sources/0/checkedAt").isNull()).isFalse();
    assertThat(firstView.at("/items/0/sources/0/checkedBy/id").asString()).isEqualTo("1");
    long second = drafts.createPublishedSafety(city, 1, first, safetyInput(source, "KEEP", "기존"));
    var secondView = safety.current(city).orElseThrow();
    assertThat(checkedAt(secondView)).isEqualTo(checkedAt(firstView));
    assertThat(secondView.at("/items/0/sources/0/checkedBy/id").asString()).isEqualTo("1");
    drafts.createPublishedSafety(city, 1, second, safetyInput(source, "KEEP", "변경"));
    var changed = safety.current(city).orElseThrow();
    assertThat(changed.at("/items/0/sources/0/checkedAt").isNull()).isTrue();
    assertThat(changed.at("/items/0/sources/0/checkedBy").isNull()).isTrue();
  }

  @Test
  void savingAndDiscardingSafetyDraftPreservesPreviousRevisionAndRejectsMissingSources() {
    long city = cities.create(1, 1, null, null, null, null, null)[0];
    String source = sources.create("https://example.org/safety-draft", "안전 출처", null).id();
    var input = (ObjectNode) json.readTree("""
        {"nameKo":null,"nameEn":null,"coordinates":null,"tagline":null,
         "tagIds":[],"photos":[],"contentItems":[],"safetyDraft":null}
        """);
    input.set("safetyDraft", safetyInput(source, "CONFIRM", "처음"));
    drafts.replace(city, 1, drafts.find(city).orElseThrow(), input);
    var saved = drafts.find(city).orElseThrow();
    String revision = saved.at("/safetyDraft/revisionId").asString();
    assertThat(saved.at("/safetyDraft/items/0/sources/0/checkedAt").isNull()).isFalse();
    input.set("safetyDraft", safetyInput(source, "KEEP", "처음"));
    drafts.replace(city, 1, saved, input);
    var resaved = drafts.find(city).orElseThrow();
    assertThat(resaved.at("/safetyDraft/revisionId").asString()).isEqualTo(revision);
    assertThat(resaved.at("/safetyDraft/items/0/sources/0/checkedAt"))
        .isEqualTo(saved.at("/safetyDraft/items/0/sources/0/checkedAt"));
    input.putNull("safetyDraft");
    assertThatThrownBy(() -> drafts.replace(city, 1, resaved, input))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SAFETY_DRAFT_CANNOT_BE_DROPPED"));
    input.set("safetyDraft", safetyInput("9223372036854775807", "CONFIRM", "잘못된 출처"));
    assertThatThrownBy(() -> drafts.replace(city, 1, resaved, input))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    assertThat(drafts.find(city).orElseThrow()).isEqualTo(resaved);
    drafts.discard(city, 1, drafts.find(city).orElseThrow());
    assertThat(drafts.find(city)).isEmpty();
  }

  @Test
  void changedContentClearsEvidenceUntilItIsConfirmedAgain() {
    long city = cities.create(1, 1, null, null, null, null, null)[0];
    String source = sources.create("https://example.org/content-evidence", "검토 출처", null).id();
    var input = (ObjectNode) json.readTree("""
        {"nameKo":null,"nameEn":null,"coordinates":null,"tagline":null,"tagIds":[],"photos":[],
         "contentItems":[{"id":null,"kind":"EXPERIENCE","title":"처음","body":"본문","displayOrder":1,
          "isSelected":true,"sources":[{"sourceDocumentId":"%s","evidenceNote":"근거","reviewAction":"CONFIRM"}]}],
         "safetyDraft":null}
        """.formatted(source));
    drafts.replace(city, 1, drafts.find(city).orElseThrow(), input);
    var saved = drafts.find(city).orElseThrow();
    assertThat(saved.at("/contentItems/0/sources/0/checkedAt").isNull()).isFalse();
    var item = (ObjectNode) input.get("contentItems").get(0);
    item.set("id", saved.at("/contentItems/0/id"));
    item.put("title", "변경");
    var evidence = (ObjectNode) item.get("sources").get(0);
    evidence.put("reviewAction", "KEEP");
    drafts.replace(city, 1, saved, input);
    var changed = drafts.find(city).orElseThrow();
    assertThat(changed.at("/contentItems/0/sources/0/checkedAt").isNull()).isTrue();
    evidence.put("reviewAction", "CONFIRM");
    drafts.replace(city, 1, changed, input);
    assertThat(drafts.find(city).orElseThrow().at("/contentItems/0/sources/0/checkedAt").isNull()).isFalse();
  }
}
