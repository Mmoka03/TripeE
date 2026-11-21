package com.tripee.backend.controller.admin;

import com.tripee.backend.service.admin.ExperienceSuggestionService;
import com.tripee.backend.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("postgres")
public class ExperienceSuggestionController {
  private final ExperienceSuggestionService suggestions;

  public ExperienceSuggestionController(ExperienceSuggestionService suggestions) {
    this.suggestions = suggestions;
  }

  @PostMapping("/api/v1/admin/cities/{cityId}/draft/experience-suggestions")
  public ExperienceSuggestionService.Response suggest(@PathVariable String cityId,
      @RequestHeader("X-Edit-Lock-Token") String token, @RequestBody JsonNode input,
      Authentication authentication, HttpServletRequest request) {
    long id;
    try {
      if (!cityId.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
      id = Long.parseLong(cityId);
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "도시 ID를 확인해 주세요.");
    }
    return suggestions.suggest(id, Long.parseLong(authentication.getName()), token, request.getSession(false), input);
  }
}
