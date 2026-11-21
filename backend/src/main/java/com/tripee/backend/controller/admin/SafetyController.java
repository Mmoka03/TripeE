package com.tripee.backend.controller.admin;

import com.tripee.backend.service.admin.SafetyService;
import com.tripee.backend.common.exception.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("postgres")
public class SafetyController {
  private final SafetyService safety;

  public SafetyController(SafetyService safety) {
    this.safety = safety;
  }

  @GetMapping("/api/v1/admin/cities/{cityId}/safety")
  public ResponseEntity<JsonNode> current(@PathVariable String cityId) {
    return response(safety.current(id(cityId)));
  }

  @PutMapping("/api/v1/admin/cities/{cityId}/safety")
  public ResponseEntity<JsonNode> publish(@PathVariable String cityId,
      @RequestHeader(value="If-Match", required=false) String ifMatch, @RequestBody JsonNode input,
      Authentication authentication, HttpServletRequest request) {
    return response(safety.publish(id(cityId), Long.parseLong(authentication.getName()), ifMatch, input, request.getSession(false)));
  }

  private static ResponseEntity<JsonNode> response(com.tripee.backend.model.admin.Versioned<JsonNode> value) {
    return ResponseEntity.ok().eTag(value.version()).body(value.value());
  }

  private static long id(String cityId) {
    try {
      if (!cityId.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
      return Long.parseLong(cityId);
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "도시 ID를 확인해 주세요.");
    }
  }
}
