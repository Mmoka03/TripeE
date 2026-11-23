package com.tripee.backend.repository.admin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SourceRepository {
  Source create(String url, String title, String publisher);
  Optional<Source> find(long id);
  List<Source> list(Long before, String query, int limit);
  record Source(String id, String url, String title, String publisher, Instant createdAt) {}
}
