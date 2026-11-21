package com.tripee.backend.city;

import org.junit.jupiter.api.Tag;

import com.tripee.backend.controller.city.*;
import com.tripee.backend.service.city.*;
import com.tripee.backend.repository.city.*;


import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("postgres")
@WebMvcTest(CityListController.class)
@Tag("controller")
class CityListControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean CityListService cities;

  @Test
  void returnsMapMarkersWithStringIdsAndOnlyPublicFields() throws Exception {
    var japan = new CitySummary.Country("7", "JP", "일본", "Japan");
    var point = new CitySummary.Coordinates(new BigDecimal("35.01"), new BigDecimal("135.77"));
    when(cities.list("JP")).thenReturn(List.of(
        new CitySummary("9007199254740993", japan, "교토", "Kyoto", point, CityVisibility.PREPARING)));

    mvc.perform(get("/api/v1/cities").param("countryCode", "JP"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().json("""
            {"items":[{
              "id":"9007199254740993", "nameKo":"교토", "nameEn":"Kyoto",
              "country":{"id":"7","code":"JP","nameKo":"일본","nameEn":"Japan"},
              "coordinates":{"latitude":35.01,"longitude":135.77}, "visibility":"PREPARING"
            }]}
            """, JsonCompareMode.STRICT));
  }

  @Test
  void emptyMapIsAnEmptyArray() throws Exception {
    mvc.perform(get("/api/v1/cities"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"items\":[]}", JsonCompareMode.STRICT));
  }

  @Test
  void reportsDatabaseFailuresWithoutExposingInternalDetails() throws Exception {
    when(cities.list(null)).thenThrow(new DataAccessResourceFailureException("private database details"));
    mvc.perform(get("/api/v1/cities"))
        .andExpect(status().isInternalServerError())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.detail").value("데이터를 조회할 수 없습니다."))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "jp", "JPN"})
  void rejectsInvalidCountryCodesBeforeQuerying(String invalidCode) throws Exception {
    mvc.perform(get("/api/v1/cities").param("countryCode", invalidCode))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
    verifyNoInteractions(cities);
  }
}
