package com.tripee.backend;

import org.junit.jupiter.api.Tag;

import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiRequestConfigurationTest.RequestController.class)
@Import(ApiRequestConfigurationTest.RequestController.class)
@Tag("controller")
class ApiRequestConfigurationTest {
  @Autowired MockMvc mvc;

  @Test
  void acceptsDocumentedFields() throws Exception {
    mvc.perform(post("/test/request").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"nameKo": "교토"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nameKo").value("교토"));
  }

  @Test
  void rejectsUnknownFieldsAsProblemJson() throws Exception {
    mvc.perform(post("/test/request").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"nameKo": "교토", "publishedAt": "2026-11-18T01:00:00Z"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void rejectsEmptyQueryParameters() throws Exception {
    mvc.perform(get("/test/request").param("q", ""))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @TestComponent
  @RestController
  static class RequestController {
    record Input(String nameKo) {}

    @PostMapping("/test/request")
    Input echo(@RequestBody Input input) {
      return input;
    }

    @GetMapping("/test/request")
    String search(@RequestParam("q") @Size(min = 1, max = 100) String query) {
      return query;
    }
  }
}
