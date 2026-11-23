package com.tripee.backend.service.admin;

import java.util.List;
import tools.jackson.databind.JsonNode;

/** The adapter must treat source text as untrusted data, never as instructions. */
public interface ExperienceSuggestionProvider {
  JsonNode generate(List<ExperienceSuggestionService.SourceInput> sources);
}
