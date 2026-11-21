package com.tripee.backend.service.admin;

import com.tripee.backend.repository.admin.SourceRepository.Source;
import java.util.List;
import tools.jackson.databind.JsonNode;

public interface SourceService {
  Source create(JsonNode input);
  Source find(String id);
  Page list(String cursor, int limit, String query);
  record Cursor(long before, String query) {}
  record Page(List<Source> items, String nextCursor) {}
}
