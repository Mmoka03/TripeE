package com.tripee.backend.repository.admin.jpa;

import com.tripee.backend.entity.admin.PhotoAssetEntity;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Profile("postgres")
public interface PhotoAssetJpaRepository extends JpaRepository<PhotoAssetEntity, Long> {
  List<PhotoAssetEntity> findByCityIdOrderByIdAsc(long cityId);

  @Query("""
      select a.storageKey from PhotoAssetEntity a
      where a.id = :id and (:admin = true or exists (
        select 1 from CityEntity c join RevisionPhotoEntity p on p.revisionId = c.publishedRevisionId
        where c.id = a.cityId and c.visibility = 'PUBLIC' and p.photoAssetId = a.id))
      """)
  Optional<String> accessibleStorageKey(@Param("id") long id, @Param("admin") boolean administrator);

  // JVM 시계 대신 기존 CURRENT_TIMESTAMP와 같은 PostgreSQL 트랜잭션 시각을 사용함.
  @Query("select current_timestamp")
  Timestamp transactionTime();
}
