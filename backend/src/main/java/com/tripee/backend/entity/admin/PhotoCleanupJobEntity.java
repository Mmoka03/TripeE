package com.tripee.backend.entity.admin;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** JDBC 쓰기와 공유하는 조회 모델이며 엔티티 변경 감지를 사용하지 않음. */
@Entity
@Table(name = "photo_cleanup_job")
@Immutable
public class PhotoCleanupJobEntity {
  @Id
  @Column(name = "storage_key") private String storageKey;
  @Column(name = "created_at") private java.time.Instant createdAt;
  protected PhotoCleanupJobEntity() {}
}
