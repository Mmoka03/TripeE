package com.tripee.backend.entity.admin;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** JDBC 쓰기와 공유하는 조회 모델이며 엔티티 변경 감지를 사용하지 않음. */
@Entity
@Table(name = "audit_event")
@Immutable
public class AuditEventEntity {
  @Id
  @Column(name = "id") private Long id;
  @Column(name = "actor_id") private Long actorId;
  @Column(name = "city_ref_id") private Long cityRefId;
  @Column(name = "revision_ref_id") private Long revisionRefId;
  @Column(name = "safety_revision_ref_id") private Long safetyRevisionRefId;
  @Column(name = "action") private String action;
  @Column(name = "snapshot_schema_version") private Integer snapshotSchemaVersion;
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "before_data") private String beforeData;
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "after_data") private String afterData;
  @Column(name = "occurred_at") private java.time.Instant occurredAt;
  protected AuditEventEntity() {}
}
