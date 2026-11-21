package com.tripee.backend.admin;

import org.junit.jupiter.api.Tag;

import com.tripee.backend.controller.admin.*;
import com.tripee.backend.service.admin.*;
import com.tripee.backend.repository.admin.*;
import com.tripee.backend.config.admin.*;


import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AdminSessionController.class, CsrfController.class})
@org.springframework.context.annotation.Import(AdminSessionService.class)
@ActiveProfiles("postgres")
@Tag("controller")
class AdminSessionTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean AdminAccounts accounts;
  @MockitoBean Clock clock;
  Instant now = Instant.parse("2026-11-19T00:00:00Z");
  MockHttpSession session;
  String token;

  @BeforeEach
  void prepare() throws Exception {
    when(clock.instant()).thenReturn(now);
    when(accounts.find("admin")).thenReturn(Optional.of(new AdminAccounts.Account(
        "9007199254740993", "A", new BCryptPasswordEncoder().encode("correct-password"), true)));
    csrf();
  }

  void csrf() throws Exception {
    var request = get("/api/v1/admin/csrf");
    if (session != null) request.session(session);
    var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
    session = (MockHttpSession) result.getRequest().getSession(false);
    token = json.readTree(result.getResponse().getContentAsByteArray()).get("token").asString();
  }

  void login() throws Exception {
    mvc.perform(post("/api/v1/admin/session").session(session).header("X-CSRF-TOKEN", token)
        .contentType("application/json").content("""
        {"loginId":"admin","password":"correct-password"}
        """))
        .andExpect(status().isOk()).andExpect(jsonPath("$.admin.id").value("9007199254740993"))
        .andExpect(jsonPath("$.admin.displayName").value("A"))
        .andExpect(jsonPath("$.idleExpiresAt").value("2026-11-19T01:00:00Z"));
  }

  @Test
  void rotatesSessionAndCsrfThenLogoutInvalidatesOnlyThisSession() throws Exception {
    String originalId = session.getId();
    String originalToken = token;
    login();
    assertThat(session.getId()).isNotEqualTo(originalId);
    mvc.perform(delete("/api/v1/admin/session").session(session).header("X-CSRF-TOKEN", originalToken))
        .andExpect(status().isForbidden());
    csrf();
    mvc.perform(delete("/api/v1/admin/session").session(session).header("X-CSRF-TOKEN", token))
        .andExpect(status().isNoContent());
    assertThat(session.isInvalid()).isTrue();
    mvc.perform(get("/api/v1/admin/session")).andExpect(status().isUnauthorized());
  }

  @Test
  void pollingDoesNotExtendActivityAndExpirationCannotBeRevived() throws Exception {
    login();
    csrf();
    when(clock.instant()).thenReturn(now.plusSeconds(3599));
    mvc.perform(get("/api/v1/admin/session").session(session))
        .andExpect(status().isOk()).andExpect(jsonPath("$.lastActivityAt").value(now.toString()));
    when(clock.instant()).thenReturn(now.plusSeconds(3600));
    mvc.perform(post("/api/v1/admin/session/activity").session(session).header("X-CSRF-TOKEN", token)
        .contentType("application/json").content("{\"event\":\"CLICK\"}"))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
  }

  @Test
  void explicitActivityExtendsExpiry() throws Exception {
    login();
    csrf();
    when(clock.instant()).thenReturn(now.plusSeconds(600));
    mvc.perform(post("/api/v1/admin/session/activity").session(session).header("X-CSRF-TOKEN", token)
        .contentType("application/json").content("{\"event\":\"NAVIGATION\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.idleExpiresAt").value("2026-11-19T01:10:00Z"));
  }

  @Test
  void unknownInactiveAndWrongPasswordAllReturnGenericCredentialsError() throws Exception {
    for (String id : new String[]{"missing", "admin", "inactive"}) {
      when(accounts.find("inactive")).thenReturn(Optional.of(new AdminAccounts.Account("2", "B",
          new BCryptPasswordEncoder().encode("wrong"), false)));
      mvc.perform(post("/api/v1/admin/session").session(session).header("X-CSRF-TOKEN", token)
          .contentType("application/json").content("{\"loginId\":\"" + id + "\",\"password\":\"wrong\"}"))
          .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
  }

  @Test
  void malformedAndUnknownFieldsAreRejected() throws Exception {
    mvc.perform(post("/api/v1/admin/session").session(session).header("X-CSRF-TOKEN", token)
        .contentType("application/json").content("{\"loginId\":\"admin\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    mvc.perform(post("/api/v1/admin/session").session(session).header("X-CSRF-TOKEN", token)
        .contentType("application/json").content("{\"loginId\":\"admin\",\"password\":\"x\",\"role\":\"admin\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }
}
