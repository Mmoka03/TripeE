package com.tripee.backend.city;

import org.junit.jupiter.api.Tag;

import com.tripee.backend.controller.city.*;
import com.tripee.backend.service.city.*;
import com.tripee.backend.repository.city.*;


import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("postgres")
@WebMvcTest(CitySearchController.class)
@Tag("controller")
class CitySearchControllerTest {
  private static final String URL = "/api/v1/search";
  private static final CitySummary.Country COUNTRY = new CitySummary.Country("9", "JP", "일본", "Japan");

  @Autowired MockMvc mvc;
  @MockitoBean CityListService cities;

  @Test
  void ranksVisibleDestinationsByExactPrefixAndSubstringThenByTypeAndNumericId() throws Exception {
    when(cities.list(null)).thenReturn(List.of(
        city("100", "West Japan"), city("11", "Japan A"), city("2", "Japan B"),
        city("9007199254740993", "Japan")));

    mvc.perform(get(URL).param("q", "  jApAn  ").param("limit", "4"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.items.length()").value(4))
        .andExpect(jsonPath("$.items[0].type").value("CITY"))
        .andExpect(jsonPath("$.items[0].city.id").value("9007199254740993"))
        .andExpect(jsonPath("$.items[0].city.coordinates.latitude").value(35))
        .andExpect(jsonPath("$.items[0].country").doesNotExist())
        .andExpect(jsonPath("$.items[1].type").value("COUNTRY"))
        .andExpect(jsonPath("$.items[1].country.code").value("JP"))
        .andExpect(jsonPath("$.items[1].visibleCityCount").value(4))
        .andExpect(jsonPath("$.items[1].city").doesNotExist())
        .andExpect(jsonPath("$.items[2].city.id").value("2"))
        .andExpect(jsonPath("$.items[3].city.id").value("11"));
  }

  @Test
  void matchesKoreanSubstringsAndReturnsAnEmptyListForLiteralWildcards() throws Exception {
    when(cities.list(null)).thenReturn(List.of(city("1", "Kyoto")));
    mvc.perform(get(URL).param("q", "토")).andExpect(jsonPath("$.items[0].city.nameKo").value("교토"));
    mvc.perform(get(URL).param("q", "%")).andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void returnsAtMostTenResultsWhenLimitIsOmitted() throws Exception {
    when(cities.list(null)).thenReturn(IntStream.rangeClosed(1, 12)
        .mapToObj(id -> city(String.valueOf(id), "Kyoto")).toList());
    mvc.perform(get(URL).param("q", "교토"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(10))
        .andExpect(jsonPath("$.items[9].city.id").value("10"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "51", "1.5", "", "xyz"})
  void rejectsInvalidLimitsBeforeReadingData(String limit) throws Exception {
    mvc.perform(get(URL).param("q", "교토").param("limit", limit))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
    verifyNoInteractions(cities);
  }

  @Test
  void requiresOneToOneHundredCharactersAfterTrimming() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isBadRequest());
    for (String invalid : List.of("  ", "가".repeat(101))) {
      mvc.perform(get(URL).param("q", invalid))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
    }
    verifyNoInteractions(cities);
  }

  private static CitySummary city(String id, String nameEn) {
    return new CitySummary(id, COUNTRY, "교토", nameEn,
        new CitySummary.Coordinates(BigDecimal.valueOf(35), BigDecimal.valueOf(135)),
        CityVisibility.PREPARING);
  }
}
