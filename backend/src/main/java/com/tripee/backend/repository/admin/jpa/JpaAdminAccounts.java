package com.tripee.backend.repository.admin.jpa;

import com.tripee.backend.repository.admin.AdminAccounts;
import com.tripee.backend.entity.admin.AdminAccountEntity;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
public class JpaAdminAccounts implements AdminAccounts {
  private final EntityManager entities;
  public JpaAdminAccounts(EntityManager entities) { this.entities = entities; }

  @Override @Transactional(readOnly = true)
  public Optional<Account> find(String loginId) {
    return entities.createQuery("select a from AdminAccountEntity a where a.loginId = :login", AdminAccountEntity.class)
        .setParameter("login", loginId).getResultStream()
        .map(a -> new Account(a.id().toString(), a.displayName(), a.passwordHash(), a.active())).findFirst();
  }
}
