package com.tripee.backend.repository.admin.jpa;

import com.tripee.backend.repository.admin.AdminDictionaryRepository;
import com.tripee.backend.model.admin.DictionaryModels.Country;
import com.tripee.backend.model.admin.DictionaryModels.Tag;
import com.tripee.backend.entity.city.CountryEntity;
import com.tripee.backend.entity.tag.TagEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
@Transactional(readOnly = true)
public class JpaAdminDictionaryRepository implements AdminDictionaryRepository {
  private final EntityManager entities;
  public JpaAdminDictionaryRepository(EntityManager entities) { this.entities = entities; }
  public List<Country> countries() {
    return entities.createQuery("select c from CountryEntity c order by c.id", CountryEntity.class).getResultStream()
        .map(c -> new Country(c.id().toString(), c.code(), c.nameKo(), c.nameEn())).toList();
  }
  public List<Tag> tags() {
    return entities.createQuery("select t from TagEntity t order by t.id", TagEntity.class).getResultStream()
        .map(t -> new Tag(t.id().toString(), t.code(), t.labelKo(), t.labelEn(), t.active())).toList();
  }
}
