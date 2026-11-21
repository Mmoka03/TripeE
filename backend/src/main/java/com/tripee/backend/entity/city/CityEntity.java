package com.tripee.backend.entity.city;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** JDBC 쓰기와 공유하는 조회 모델이며 엔티티 변경 감지를 사용하지 않음. */
@Entity
@Table(name = "city")
@Immutable
public class CityEntity {
  @Id
  @Column(name = "id") private Long id;
  @Column(name = "country_id") private Long countryId;
  @Column(name = "visibility") private String visibility;
  @Column(name = "published_revision_id") private Long publishedRevisionId;
  @Column(name = "draft_revision_id") private Long draftRevisionId;
  @Column(name = "published_safety_revision_id") private Long publishedSafetyRevisionId;
  protected CityEntity() {}
}
