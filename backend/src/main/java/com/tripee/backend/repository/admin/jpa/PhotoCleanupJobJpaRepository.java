package com.tripee.backend.repository.admin.jpa;

import com.tripee.backend.entity.admin.PhotoCleanupJobEntity;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

@Profile("postgres")
public interface PhotoCleanupJobJpaRepository extends Repository<PhotoCleanupJobEntity, String> {
  @Query("""
      select j.storageKey from PhotoCleanupJobEntity j
      where not exists (select 1 from PhotoAssetEntity a where a.storageKey = j.storageKey)
      order by j.createdAt
      """)
  List<String> pending(Pageable page);

  @Modifying
  @Query("delete from PhotoCleanupJobEntity j where j.storageKey = :key")
  void deleteCompleted(@Param("key") String key);
}
