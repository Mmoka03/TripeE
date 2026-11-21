package com.tripee.backend.repository.admin.jpa;

import com.tripee.backend.entity.admin.PhotoAssetEntity;
import com.tripee.backend.repository.admin.PhotoRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
@Transactional(readOnly = true)
public class JpaPhotoRepository implements PhotoRepository {
  private final PhotoAssetJpaRepository assets;

  public JpaPhotoRepository(PhotoAssetJpaRepository assets) { this.assets = assets; }

  @Override
  public List<PhotoAsset> list(long cityId) {
    return assets.findByCityIdOrderByIdAsc(cityId).stream().map(JpaPhotoRepository::map).toList();
  }

  @Override
  public Optional<String> storageKey(long id, boolean administrator) {
    return assets.accessibleStorageKey(id, administrator);
  }

  @Override @Transactional
  public PhotoAsset insert(long cityId, String key, String sourceUrl, String creatorName, String licenseName,
                           String licenseUrl, String usageNote, boolean confirmed) {
    var checkedAt = confirmed ? assets.transactionTime().toInstant() : null;
    // 이후 JDBC 초안 저장·삭제가 같은 트랜잭션에서 바로 참조할 수 있게 flush함.
    return map(assets.saveAndFlush(new PhotoAssetEntity(cityId, key, sourceUrl, creatorName, licenseName,
        licenseUrl, usageNote, checkedAt)));
  }

  private static PhotoAsset map(PhotoAssetEntity entity) {
    String id = entity.id().toString();
    return new PhotoAsset(id, entity.cityId().toString(), "/api/v1/media/photo-assets/" + id,
        entity.sourceUrl(), entity.creatorName(), entity.licenseName(), entity.licenseUrl(),
        entity.usageNote(), entity.rightsCheckedAt());
  }
}
