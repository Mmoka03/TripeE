package com.tripee.backend.admin;

import com.tripee.backend.PostgresIntegrationTestBase;
import com.tripee.backend.common.exception.ApiException;
import com.tripee.backend.repository.admin.AdminCityRepository;
import com.tripee.backend.model.admin.CityModels.City;
import java.math.BigDecimal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@Tag("postgres")
@Tag("repository")
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TRIPEE_TEST_ISOLATED_DB", matches = "true")
@Transactional
class AdminCityRepositoryPostgresTest extends PostgresIntegrationTestBase {
  @Autowired AdminCityRepository cities;
  @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;

  @Test
  void filtersAndPaginatesCitiesByVisibilityAndLiteralSearch() {
    long first = cities.create(1, 1, "테스트 교토", "Test Kyoto", "여행", BigDecimal.valueOf(35), BigDecimal.valueOf(135))[0];
    long second = cities.create(1, 1, "테스트 오사카", "Test Osaka", "여행", BigDecimal.valueOf(34), BigDecimal.valueOf(135))[0];
    assertThat(cities.list(null, "HIDDEN", "테스트", 1)).extracting(City::id)
        .containsExactly(Long.toString(second), Long.toString(first));
    assertThat(cities.list(second, "HIDDEN", "테스트", 1)).extracting(City::id).containsExactly(Long.toString(first));
    assertThat(cities.list(null, "HIDDEN", "kyoto", 10)).extracting(City::id).containsExactly(Long.toString(first));
    assertThat(cities.list(null, "PREPARING", "테스트", 10)).isEmpty();
    assertThat(cities.list(null, "HIDDEN", "%", 10)).isEmpty();
  }

  @Test
  void rejectsUnknownCountryBeforeCreatingCity() {
    assertThatThrownBy(() -> cities.create(Long.MAX_VALUE, 1, null, null, null, null, null))
        .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("VALIDATION_FAILED"));
  }

  @Test
  void treatsSearchMetacharactersLiterallyAndIncludesCountriesWithNullNames() {
    long literal = cities.create(1, 1, "퍼센트", "100%_\\ Kyoto", null, null, null)[0];
    long blank = cities.create(1, 1, null, null, null, null, null)[0];
    for (String query : java.util.List.of("%", "_", "\\", "100%_\\", "kYoTo")) {
      assertThat(cities.list(null, "", query, 100)).extracting(City::id).containsExactly(Long.toString(literal));
    }
    assertThat(cities.list(null, "", "JAPAN", 100)).extracting(City::id)
        .contains(Long.toString(literal), Long.toString(blank));
    assertThat(cities.list(null, "", "일본", 100)).extracting(City::id).contains(Long.toString(blank));
    assertThat(cities.list(blank, "", "", 100)).extracting(City::id).doesNotContain(Long.toString(blank));
    assertThat(cities.find(blank).nameKo()).isNull();
    assertThat(cities.find(blank).publishedRevisionId()).isNull();
    assertThat(cities.hasDisplayableRevision(blank)).isFalse();
    assertThat(cities.hasDisplayableRevision(Long.MAX_VALUE)).isFalse();
    assertThatThrownBy(() -> cities.find(Long.MAX_VALUE)).isInstanceOfSatisfying(ApiException.class,
        error -> assertThat(error.code()).isEqualTo("CITY_NOT_FOUND"));
  }

  @Test
  void queriesSeeJdbcWritesImmediatelyAndUsePublishedNamesBeforeDraftNames() {
    long[] ids = cities.create(1, 1, "공개 이름", "Published name", "소개", BigDecimal.ZERO, BigDecimal.ZERO);
    assertThat(cities.hasDisplayableRevision(ids[0])).isTrue();
    var before = cities.find(ids[0]);
    cities.changeVisibility(ids[0], 1, "PREPARING", before);
    assertThat(cities.find(ids[0]).visibility()).isEqualTo("PREPARING");
    RepositoryFixtures.publish(jdbc, ids[0], ids[1]);
    long draft = jdbc.sql("""
        insert into city_revision(city_id, revision_no, name_ko, name_en, created_by, updated_by)
        values (:city, 2, '초안 이름', 'Draft name', 1, 1) returning id
        """).param("city", ids[0]).query(Long.class).single();
    jdbc.sql("update city set draft_revision_id=:draft where id=:city")
        .param("draft", draft).param("city", ids[0]).update();
    assertThat(cities.find(ids[0]).nameEn()).isEqualTo("Published name");
    assertThat(cities.list(null, "PUBLIC", "Draft name", 10)).isEmpty();
    assertThat(cities.list(null, "PUBLIC", "Published name", 10)).extracting(City::id)
        .containsExactly(Long.toString(ids[0]));
    cities.changeVisibility(ids[0], 1, "HIDDEN", cities.find(ids[0]));
    assertThat(cities.find(ids[0]).visibility()).isEqualTo("HIDDEN");
    assertThat(jdbc.sql("select action from audit_event where city_ref_id=:city order by id desc limit 1")
        .param("city", ids[0]).query(String.class).single()).isEqualTo("UNPUBLISH");
  }
}
