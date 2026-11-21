package com.tripee.backend.controller.city;

import com.tripee.backend.controller.city.*;
import com.tripee.backend.service.city.*;
import com.tripee.backend.repository.city.*;


import com.tripee.backend.common.exception.InvalidQueryException;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("postgres")
public class CitySearchController {
  private static final Pattern OUTER_WHITESPACE = Pattern.compile("(?U)^\\s+|\\s+$");
  private final CityListService cities;

  public CitySearchController(CityListService cities) {
    this.cities = cities;
  }

  @GetMapping("/api/v1/search")
  CitySearch search(@RequestParam("q") String q,
                    @RequestParam(name = "limit", required = false) String limit) {
    String query = OUTER_WHITESPACE.matcher(q).replaceAll("");
    if (query.isEmpty() || query.codePointCount(0, query.length()) > 100) {
      throw new InvalidQueryException("검색어는 앞뒤 공백을 제외하고 1~100자로 입력해 주세요.");
    }
    int resultLimit = parseLimit(limit);
    return CitySearch.fromVisibleCities(cities.list(null), query, resultLimit);
  }

  private static int parseLimit(String limit) {
    if (limit == null) return 10;
    try {
      int value = Integer.parseInt(limit);
      if (value >= 1 && value <= 50) return value;
    } catch (NumberFormatException ignored) {
      // 범위 밖의 값과 같은 쿼리 오류로 처리함.
    }
    throw new InvalidQueryException("limit은 1~50 사이의 정수여야 합니다.");
  }
}
