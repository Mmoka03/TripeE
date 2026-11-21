package com.tripee.backend.controller.admin;

import com.tripee.backend.service.admin.AuditService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("postgres")
public class AuditController {
  private final AuditService audit;

  public AuditController(AuditService audit) {
    this.audit = audit;
  }

  @GetMapping("/api/v1/admin/audit-events")
  public AuditService.Page list(@RequestParam(required=false) String cityRefId,
      @RequestParam(required=false) String cursor, @RequestParam(defaultValue="20") int limit) {
    return audit.list(cityRefId, cursor, limit);
  }

  @GetMapping("/api/v1/admin/audit-events/{eventId}")
  public JsonNode find(@PathVariable String eventId) {
    return audit.find(eventId);
  }

  @GetMapping("/api/v1/admin/cities/{cityId}/revisions/{revisionId}")
  public JsonNode historicalPublication(@PathVariable String cityId, @PathVariable String revisionId) {
    return audit.historicalPublication(cityId, revisionId);
  }
}
