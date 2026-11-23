package com.tripee.backend.admin;

import com.tripee.backend.controller.admin.EditLockController;
import com.tripee.backend.service.admin.EditLockService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("controller")
@WebMvcTest(EditLockController.class)
@ActiveProfiles("postgres")
class EditLockControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean EditLockService locks;

  @Test
  void anonymousUserCannotInspectLocks() throws Exception {
    mvc.perform(get("/api/v1/admin/cities/1/edit-lock"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    verifyNoInteractions(locks);
  }
}
