package com.tripee.backend.city;

import com.tripee.backend.controller.city.*;
import com.tripee.backend.service.city.*;
import com.tripee.backend.repository.city.*;


import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@Tag("repository")
class CityListPostgresTest extends com.tripee.backend.PostgresIntegrationTestBase {
  @Autowired CityListRepository cities;
  @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;

  @Test
  @Sql("/city-map-fixture.sql")
  void readsOnlyDisplayableMarkersAndPrefersPublishedNames() {
    assertThat(cities.list(null)).extracting(CitySummary::nameEn)
        .containsExactly("Kyoto", "Naples");
    assertThat(cities.list("JP")).extracting(CitySummary::id).containsExactly("1");
    assertThat(cities.list("IT")).extracting(CitySummary::visibility)
        .containsExactly(CityVisibility.PREPARING);
    assertThat(cities.list("CN")).isEmpty();
  }

  @Test
  @Sql("/city-map-fixture.sql")
  void requiresCompleteSelectedRevisionAndImmediatelyReflectsJdbcVisibilityChanges() {
    // 공개본이 불완전해도 완전한 초안으로 우회해 노출하지 않음.
    jdbc.sql("update city_revision set name_ko=null where id=10").update();
    assertThat(cities.list("JP")).isEmpty();
    jdbc.sql("update city_revision set name_ko='교토', longitude=null where id=10").update();
    assertThat(cities.list("JP")).isEmpty();
    jdbc.sql("update city_revision set longitude=135.77 where id=10").update();
    assertThat(cities.list("JP")).extracting(CitySummary::nameEn).containsExactly("Kyoto");
    jdbc.sql("update city set visibility='HIDDEN' where id=1").update();
    assertThat(cities.list("JP")).isEmpty();
    jdbc.sql("update city set visibility='PREPARING' where id=1").update();
    assertThat(cities.list("JP")).extracting(CitySummary::nameEn).containsExactly("Kyoto");
    assertThat(cities.list("")).isEmpty();
    assertThat(cities.list("jp")).isEmpty();
  }
}
