package com.tripee.backend.controller.city;

import com.tripee.backend.controller.city.*;
import com.tripee.backend.service.city.*;
import com.tripee.backend.repository.city.*;


import com.tripee.backend.common.exception.ApiException;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("postgres")
public class CityDetailController {
  private static final Pattern CITY_ID = Pattern.compile("[1-9][0-9]{0,18}");
  private final CityDetailService cities;

  public CityDetailController(CityDetailService cities) {
    this.cities = cities;
  }

  @GetMapping("/api/v1/cities/{cityId}")
  public JsonNode detail(@PathVariable("cityId") String cityId) {
    return cities.find(parseId(cityId)).orElseThrow(() ->
        new ApiException(HttpStatus.NOT_FOUND, "CITY_NOT_FOUND", "도시를 찾을 수 없어요."));
  }

  private static long parseId(String input) {
    if (CITY_ID.matcher(input).matches()) {
      try {
        return Long.parseLong(input);
      } catch (NumberFormatException ignored) {
        // 19자리라도 PostgreSQL bigint 최댓값을 넘을 수 있음.
      }
    }
    throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "도시 ID 형식을 확인해 주세요.");
  }
}
