package com.tripee.backend.repository.admin;

import java.util.List;

public interface PhotoCleanupRepository {
  List<String> pending();
  void completed(String key);
}
