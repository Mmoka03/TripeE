package com.tripee.backend.repository.admin;

import java.util.Optional;

public interface AdminAccounts {
  Optional<Account> find(String loginId);
  record Account(String id, String displayName, String passwordHash, boolean active) {}
}
