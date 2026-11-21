package com.tripee.backend.entity.city;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** JDBC 쓰기와 공유하는 조회 모델이며 엔티티 변경 감지를 사용하지 않음. */
@Entity
@Table(name = "city_revision")
@Immutable
public class CityRevisionEntity {
  @Id
  @Column(name = "id") private Long id;
  @Column(name = "city_id") private Long cityId;
  @Column(name = "revision_no") private Integer revisionNo;
  @Column(name = "name_ko") private String nameKo;
  @Column(name = "name_en") private String nameEn;
  @Column(name = "latitude") private java.math.BigDecimal latitude;
  @Column(name = "longitude") private java.math.BigDecimal longitude;
  @Column(name = "edit_version") private Long editVersion;
  @Column(name = "published_at") private java.time.Instant publishedAt;
  @Column(name = "published_by") private Long publishedBy;
  protected CityRevisionEntity() {}
}
