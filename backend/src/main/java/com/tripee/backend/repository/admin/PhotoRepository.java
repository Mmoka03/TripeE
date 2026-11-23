package com.tripee.backend.repository.admin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository {
  List<PhotoAsset> list(long cityId);
  Optional<String> storageKey(long id, boolean administrator);
  PhotoAsset insert(long cityId, String key, String sourceUrl, String creatorName, String licenseName,
                    String licenseUrl, String usageNote, boolean confirmed);

  record PhotoAsset(String id, String cityId, String imageUrl, String sourceUrl,
                    String creatorName, String licenseName, String licenseUrl,
                    String usageNote, Instant rightsCheckedAt) {}
}
