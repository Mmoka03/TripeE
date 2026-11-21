package com.tripee.backend.entity.admin;

import jakarta.persistence.*;

@Entity
@Table(name = "admin_account")
public class AdminAccountEntity {
  @Id private Long id;
  @Column(name = "login_id") private String loginId;
  @Column(name = "password_hash") private String passwordHash;
  @Column(name = "display_name") private String displayName;
  @Column(name = "is_active") private boolean active;
  protected AdminAccountEntity() {}
  public Long id() { return id; }
  public String passwordHash() { return passwordHash; }
  public String displayName() { return displayName; }
  public boolean active() { return active; }
}
