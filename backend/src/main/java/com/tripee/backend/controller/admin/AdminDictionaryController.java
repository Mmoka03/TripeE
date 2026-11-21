package com.tripee.backend.controller.admin;

import com.tripee.backend.model.admin.DictionaryModels.Country;
import com.tripee.backend.model.admin.DictionaryModels.Tag;

import com.tripee.backend.controller.admin.*;
import com.tripee.backend.service.admin.*;
import com.tripee.backend.repository.admin.*;
import com.tripee.backend.config.admin.*;


import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("postgres")
public class AdminDictionaryController {
  private final AdminDictionaryService dictionaries;

  public AdminDictionaryController(AdminDictionaryService dictionaries) {
    this.dictionaries = dictionaries;
  }

  @GetMapping("/api/v1/admin/countries")
  public Items<Country> countries() {
    return new Items<>(dictionaries.countries());
  }

  @GetMapping("/api/v1/admin/tags")
  public Items<Tag> tags() {
    return new Items<>(dictionaries.tags());
  }

  public record Items<T>(List<T> items) {}
}
