package com.tripee.backend.tag;

import org.junit.jupiter.api.Tag;

import com.tripee.backend.controller.tag.*;
import com.tripee.backend.repository.tag.*;
import com.tripee.backend.service.tag.TagService;


import com.tripee.backend.config.admin.AdminSecurity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("postgres")
@WebMvcTest(TagController.class)
@Import(AdminSecurity.class)
@Tag("controller")
class TagControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean TagService tags;

  @Test
  void returnsActiveTagWithBothLabelsAndStringId() throws Exception {
    when(tags.listActive()).thenReturn(List.of(
        new TagRepository.PublicTag("9007199254740993", "HISTORY", "역사", "History", true)));
    mvc.perform(get("/api/v1/tags"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().json("""
            {"items":[{"id":"9007199254740993","code":"HISTORY","labelKo":"역사",
                       "labelEn":"History","isActive":true}]}
            """, JsonCompareMode.STRICT));
  }

  @Test
  void emptyDictionaryIsAnEmptyArray() throws Exception {
    mvc.perform(get("/api/v1/tags"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"items\":[]}", JsonCompareMode.STRICT));
  }
}
