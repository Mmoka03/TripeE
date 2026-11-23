package com.tripee.backend.controller.tag;

import com.tripee.backend.controller.tag.*;
import com.tripee.backend.repository.tag.*;
import com.tripee.backend.service.tag.TagService;


import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("postgres")
public class TagController {
  private final TagService tags;

  public TagController(TagService tags) {
    this.tags = tags;
  }

  @GetMapping("/api/v1/tags")
  public TagList list() {
    return new TagList(tags.listActive());
  }

  public record TagList(List<TagRepository.PublicTag> items) {}
}
