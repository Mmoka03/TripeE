package com.tripee.backend.admin;

import org.junit.jupiter.api.Tag;

import com.tripee.backend.controller.admin.*;
import com.tripee.backend.service.admin.*;
import com.tripee.backend.repository.admin.*;
import com.tripee.backend.config.admin.*;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("controller")
class AdminBoundaryTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void issuesSessionCsrfBeforeLogin() throws Exception {
    mvc.perform(get("/api/v1/admin/csrf"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void anonymousDraftReadGetsJsonInsteadOfLoginRedirect() throws Exception {
    mvc.perform(get("/api/v1/admin/cities/1/draft"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.instance").value("/api/v1/admin/cities/1/draft"))
        .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  void draftWriteWithoutCsrfIsRejected() throws Exception {
    mvc.perform(put("/api/v1/admin/cities/1/draft").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
  }

  @Test
  void issuedTokenWorksOnlyWithItsOriginalSessionAndDoesNotGrantAdminAccess() throws Exception {
    var issued = mvc.perform(get("/api/v1/admin/csrf")).andReturn();
    var session = (MockHttpSession) issued.getRequest().getSession(false);
    var token = json.readTree(issued.getResponse().getContentAsByteArray()).get("token").asString();

    mvc.perform(put("/api/v1/admin/cities/1/draft").session(session).header("X-CSRF-TOKEN", token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    mvc.perform(put("/api/v1/admin/cities/1/draft").header("X-CSRF-TOKEN", token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
  }
}
