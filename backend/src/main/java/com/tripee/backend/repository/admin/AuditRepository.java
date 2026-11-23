package com.tripee.backend.repository.admin;

import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

public interface AuditRepository {
  Optional<JsonNode> find(long id);
  List<JsonNode> list(Long cityId, Long before, int limit);
  Optional<JsonNode> historicalPublication(long cityId, long revisionId);
}
