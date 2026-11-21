package com.tripee.backend.service.admin;

import java.util.List;
import com.tripee.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

public class UnavailableExperienceSuggestionProvider implements ExperienceSuggestionProvider {
  @Override
  public JsonNode generate(List<ExperienceSuggestionService.SourceInput> sources) {
    throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_ERROR", "AI 제공자가 아직 설정되지 않았습니다.");
  }
}
