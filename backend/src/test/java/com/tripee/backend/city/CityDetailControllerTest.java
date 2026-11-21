package com.tripee.backend.city;

import org.junit.jupiter.api.Tag;

import com.tripee.backend.controller.city.*;
import com.tripee.backend.service.city.*;
import com.tripee.backend.repository.city.*;


import java.util.Optional;
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
@WebMvcTest(CityDetailController.class)
@Tag("controller")
class CityDetailControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean CityDetailService cities;

  @ParameterizedTest
  @ValueSource(strings = {"0", "01", "-1", "+1", "1.0", "a", "9223372036854775808"})
  void rejectsMalformedIdsBeforeQuerying(String id) throws Exception {
    mvc.perform(get("/api/v1/cities/{cityId}", id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    verifyNoInteractions(cities);
  }

  @Test
  void returnsTheSameProblemForEveryUnavailableCity() throws Exception {
    when(cities.find(Long.MAX_VALUE)).thenReturn(Optional.empty());
    mvc.perform(get("/api/v1/cities/{cityId}", Long.MAX_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value("CITY_NOT_FOUND"));
  }
}
