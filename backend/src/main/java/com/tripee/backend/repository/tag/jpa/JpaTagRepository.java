package com.tripee.backend.repository.tag.jpa;

import com.tripee.backend.entity.tag.TagEntity;
import com.tripee.backend.repository.tag.TagRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
public class JpaTagRepository implements TagRepository {
  private final EntityManager entities;
  public JpaTagRepository(EntityManager entities) { this.entities = entities; }
  @Override @Transactional(readOnly = true)
  public List<PublicTag> listActive() {
    return entities.createQuery("select t from TagEntity t where t.active = true order by t.id", TagEntity.class)
        .getResultStream().map(t -> new PublicTag(t.id().toString(), t.code(), t.labelKo(), t.labelEn(), t.active())).toList();
  }
}
