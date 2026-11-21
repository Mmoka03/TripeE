package com.tripee.backend.service.city;

import com.tripee.backend.service.city.*;
import com.tripee.backend.repository.city.*;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record CitySearch(List<CitySearch.Result> items) {
  public sealed interface Result permits CityResult, CountryResult {
    String type();
  }

  public record CityResult(String type, CitySummary city) implements Result {}
  public record CountryResult(String type, CitySummary.Country country, int visibleCityCount) implements Result {}
  private record Match(int rank, long id, Result result) {}

  public static CitySearch fromVisibleCities(List<CitySummary> cities, String query, int limit) {
    String term = query.toLowerCase(Locale.ROOT);
    List<Match> matches = new ArrayList<>();
    Map<CitySummary.Country, Integer> visibleCityCounts = new HashMap<>();
    for (CitySummary city : cities) {
      visibleCityCounts.merge(city.country(), 1, Integer::sum);
      int rank = matchRank(city.nameKo(), city.nameEn(), term);
      if (rank < NO_MATCH) {
        matches.add(new Match(rank, Long.parseLong(city.id()), new CityResult("CITY", city)));
      }
    }
    visibleCityCounts.forEach((country, count) -> {
      int rank = matchRank(country.nameKo(), country.nameEn(), term);
      if (rank < NO_MATCH) {
        matches.add(new Match(rank, Long.parseLong(country.id()), new CountryResult("COUNTRY", country, count)));
      }
    });
    return new CitySearch(matches.stream()
        .sorted(Comparator.comparingInt(Match::rank)
            .thenComparing(match -> match.result().type())
            .thenComparingLong(Match::id))
        .limit(limit)
        .map(Match::result)
        .toList());
  }

  private static final int NO_MATCH = 3;

  private static int matchRank(String nameKo, String nameEn, String term) {
    return Math.min(matchRank(nameKo, term), matchRank(nameEn, term));
  }

  private static int matchRank(String name, String term) {
    String normalized = name.toLowerCase(Locale.ROOT);
    if (normalized.equals(term)) return 0;
    if (normalized.startsWith(term)) return 1;
    if (normalized.contains(term)) return 2;
    return NO_MATCH;
  }
}
