package com.tripee.backend.entity.admin;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** JDBC 쓰기와 공유하는 조회 모델이며 엔티티 변경 감지를 사용하지 않음. */
@Entity
@Table(name = "revision_photo")
@Immutable
public class RevisionPhotoEntity {
  @Id
  @Column(name = "id") private Long id;
  @Column(name = "revision_id") private Long revisionId;
  @Column(name = "photo_asset_id") private Long photoAssetId;
  protected RevisionPhotoEntity() {}
}
