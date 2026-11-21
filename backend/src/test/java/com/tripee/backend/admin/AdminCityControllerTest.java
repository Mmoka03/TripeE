package com.tripee.backend.admin;

import org.junit.jupiter.api.Tag;

import com.tripee.backend.controller.admin.*;
import com.tripee.backend.service.admin.*;
import com.tripee.backend.repository.admin.*;
import com.tripee.backend.config.admin.*;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCityController.class)
@ActiveProfiles("postgres")
@Import(AdminSecurity.class)
@Tag("controller")
class AdminCityControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean AdminCityService cities;

  @Test
  void anonymousUserCannotReadAdminCity() throws Exception {
    mvc.perform(get("/api/v1/admin/cities/1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    verifyNoInteractions(cities);
  }
}
