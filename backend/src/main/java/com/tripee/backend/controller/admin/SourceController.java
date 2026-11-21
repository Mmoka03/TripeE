package com.tripee.backend.controller.admin;

import com.tripee.backend.repository.admin.SourceRepository.Source;
import com.tripee.backend.service.admin.SourceService;
import java.net.URI;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("postgres")
public class SourceController {
  private final SourceService sources;

  public SourceController(SourceService sources) {
    this.sources = sources;
  }

  @GetMapping("/api/v1/admin/source-documents")
  public SourceService.Page list(@RequestParam(required=false) String cursor,
      @RequestParam(defaultValue="20") int limit, @RequestParam(required=false) String q) {
    return sources.list(cursor, limit, q);
  }

  @GetMapping("/api/v1/admin/source-documents/{sourceId}")
  public Source find(@PathVariable String sourceId) {
    return sources.find(sourceId);
  }

  @PostMapping("/api/v1/admin/source-documents")
  public ResponseEntity<Source> create(@RequestBody JsonNode input) {
    Source source = sources.create(input);
    return ResponseEntity.created(URI.create("/api/v1/admin/source-documents/" + source.id())).body(source);
  }
}
