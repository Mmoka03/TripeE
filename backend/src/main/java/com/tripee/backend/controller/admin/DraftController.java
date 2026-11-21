package com.tripee.backend.controller.admin;

import com.tripee.backend.service.admin.DraftService;
import com.tripee.backend.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("postgres")
public class DraftController {
  private final DraftService drafts;

  public DraftController(DraftService drafts) {
    this.drafts = drafts;
  }

  @PutMapping("/api/v1/admin/cities/{cityId}/draft")
  public ResponseEntity<JsonNode> replace(@PathVariable String cityId,
      @RequestHeader("X-Edit-Lock-Token") String token,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @RequestBody JsonNode input, Authentication authentication, HttpServletRequest request) {
    return response(drafts.replace(id(cityId), Long.parseLong(authentication.getName()), token,
        ifMatch, input, request.getSession(false)));
  }

  @GetMapping("/api/v1/admin/cities/{cityId}/draft")
  public ResponseEntity<JsonNode> find(@PathVariable String cityId,
      @RequestHeader("X-Edit-Lock-Token") String token, Authentication authentication, HttpServletRequest request) {
    return response(drafts.find(id(cityId), Long.parseLong(authentication.getName()), token, request.getSession(false)));
  }

  @PostMapping("/api/v1/admin/cities/{cityId}/draft")
  public ResponseEntity<JsonNode> create(@PathVariable String cityId,
      @RequestHeader("X-Edit-Lock-Token") String token, Authentication authentication, HttpServletRequest request) {
    var draft = drafts.create(id(cityId), Long.parseLong(authentication.getName()), token, request.getSession(false));
    return ResponseEntity.created(java.net.URI.create("/api/v1/admin/cities/" + cityId + "/draft"))
        .eTag(draft.version()).body(draft.value());
  }

  @PostMapping("/api/v1/admin/cities/{cityId}/draft/preview")
  public DraftService.Preview preview(@PathVariable String cityId,
      @RequestHeader("X-Edit-Lock-Token") String token, @RequestBody JsonNode expected,
      Authentication authentication, HttpServletRequest request) {
    return drafts.preview(id(cityId), Long.parseLong(authentication.getName()), token, expected, request.getSession(false));
  }

  @PostMapping("/api/v1/admin/cities/{cityId}/draft/publications")
  public ResponseEntity<JsonNode> publish(@PathVariable String cityId,
      @RequestHeader("X-Edit-Lock-Token") String token, @RequestBody JsonNode expected,
      Authentication authentication, HttpServletRequest request) {
    var published = drafts.publish(id(cityId), Long.parseLong(authentication.getName()), token, expected, request.getSession(false));
    return ResponseEntity.created(java.net.URI.create("/api/v1/admin/cities/" + cityId + "/revisions/" + published.revisionId()))
        .body(published.city());
  }

  @PostMapping("/api/v1/admin/cities/{cityId}/draft/safety-reviews")
  public ResponseEntity<JsonNode> reviewSafety(@PathVariable String cityId,
      @RequestHeader("X-Edit-Lock-Token") String token, @RequestBody JsonNode input,
      Authentication authentication, HttpServletRequest request) {
    return response(drafts.reviewSafety(id(cityId), Long.parseLong(authentication.getName()), token, input, request.getSession(false)));
  }

  @DeleteMapping("/api/v1/admin/cities/{cityId}/draft")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void discard(@PathVariable String cityId, @RequestHeader("X-Edit-Lock-Token") String token,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      Authentication authentication, HttpServletRequest request) {
    drafts.discard(id(cityId), Long.parseLong(authentication.getName()), token, ifMatch, request.getSession(false));
  }

  private static ResponseEntity<JsonNode> response(com.tripee.backend.model.admin.Versioned<JsonNode> value) {
    return ResponseEntity.ok().eTag(value.version()).body(value.value());
  }

  private static long id(String cityId) {
    long id;
    try {
      if (!cityId.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
      id = Long.parseLong(cityId);
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "도시 ID를 확인해 주세요.");
    }
    return id;
  }
}
