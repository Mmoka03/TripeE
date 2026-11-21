package com.tripee.backend.service.city;

import com.tripee.backend.common.exception.ApiException;
import com.tripee.backend.repository.city.CityDetailRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
@Profile("postgres")
public class CityDetailService {
  private final CityDetailRepository cities;

  public CityDetailService(CityDetailRepository cities) {
    this.cities = cities;
  }

  public Optional<JsonNode> find(long cityId) {
    return cities.find(cityId).map(CityDetailService::verifyPublishedFields);
  }

  private static JsonNode verifyPublishedFields(JsonNode detail) {
    if (!"PUBLIC".equals(detail.get("visibility").asString())) {
      return detail;
    }
    // DDL만으로는 게시 필수 항목과 출처의 완전성을 보장할 수 없음.
    for (String section : List.of("photos", "experiences", "visitPeriods", "arrival", "localTransport")) {
      require(!detail.get(section).isEmpty());
    }
    for (JsonNode photo : detail.get("photos")) {
      require(photo.get("altText").isString());
    }
    for (String section : List.of("experiences", "visitPeriods", "arrival", "localTransport", "considerations")) {
      for (JsonNode item : detail.get(section)) {
        verifyContent(item);
      }
    }
    var safety = detail.get("safety");
    require(safety.isObject() && safety.get("publishedAt").isString());
    for (JsonNode item : safety.get("items")) {
      verifyContent(item);
      for (String field : List.of("scopeType", "originalScopeText", "cityCoverage", "cityScopeNote")) {
        require(item.get(field).isString());
      }
    }
    return detail;
  }

  private static void verifyContent(JsonNode item) {
    require(item.get("title").isString() && item.get("body").isString());
    require(item.get("sources").isArray() && !item.get("sources").isEmpty());
  }

  private static void require(boolean complete) {
    if (!complete) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "도시 정보를 조회할 수 없어요.");
    }
  }
}
