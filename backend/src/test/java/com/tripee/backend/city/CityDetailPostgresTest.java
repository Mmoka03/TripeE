package com.tripee.backend.city;

import com.tripee.backend.controller.city.*;
import com.tripee.backend.service.city.*;
import com.tripee.backend.repository.city.*;


import com.tripee.backend.common.exception.ApiException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("postgres")
@ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@Sql("/city-detail-fixture.sql")
@Tag("integration")
class CityDetailPostgresTest extends com.tripee.backend.PostgresIntegrationTestBase {
  @Autowired CityDetailService cities;
  @Autowired JdbcClient jdbc;

  @Test
  void returnsOnlyThePublishedContentAndCurrentSafetyRevision() {
    var detail = cities.find(1).orElseThrow();
    assertThat(detail.get("nameKo").asString()).isEqualTo("교토");
    assertThat(detail.get("publishedAt").asString()).isEqualTo("2026-11-18T00:00:00.000000Z");
    assertThat(detail.at("/tags/0/code").asString()).isEqualTo("NATURE");
    assertThat(detail.at("/photos/0/imageUrl").asString()).isEqualTo("/api/v1/media/photo-assets/9007199254740993");
    assertThat(detail.at("/photos/0/assetId").isString()).isTrue();
    assertThat(detail.get("experiences").size()).isEqualTo(3);
    assertThat(detail.at("/experiences/2/title").asString()).isEqualTo("경험 3");
    assertThat(detail.at("/experiences/0/sources").size()).isEqualTo(1);
    assertThat(detail.at("/experiences/0/sources/0/title").asString()).isEqualTo("확인한 출처");
    assertThat(detail.at("/arrival/0/title").asString()).isEqualTo("도착");
    assertThat(detail.at("/visitPeriods/0/title").asString()).isEqualTo("계절");
    assertThat(detail.at("/localTransport/0/title").asString()).isEqualTo("이동");
    assertThat(detail.get("considerations").size()).isZero();
    assertThat(detail.at("/safety/revisionId").asString()).isEqualTo("21");
    assertThat(detail.at("/safety/items/0/temporalStatus").asString()).isEqualTo("EXPIRED");
    assertThat(detail.at("/safety/items/1/temporalStatus").asString()).isEqualTo("CURRENT");
    assertThat(detail.at("/safety/items/2/temporalStatus").asString()).isEqualTo("UPCOMING");
    assertThat(detail.at("/safety/items/0/sources/0/sourceDocumentId").asString()).isEqualTo("1");
  }

  @Test
  void preparingOnlyExposesMarkerAndMessageWhileHiddenAndDeletedCitiesAreAbsent() {
    var detail = cities.find(2).orElseThrow();
    assertThat(detail.propertyNames()).containsExactlyInAnyOrder(
        "id", "country", "nameKo", "nameEn", "coordinates", "visibility", "message");
    assertThat(detail.get("message").asString()).isEqualTo("도시 정보를 준비 중이에요.");
    assertThat(cities.find(3)).isEmpty();
    assertThat(cities.find(4)).isEmpty();
  }

  @Test
  void incompletePublishedDataDoesNotProduceAPublicResponse() {
    jdbc.sql("DELETE FROM pg_temp.content_source WHERE content_item_id = 7").update();
    assertThatThrownBy(() -> cities.find(1)).isInstanceOf(ApiException.class);
  }
}
