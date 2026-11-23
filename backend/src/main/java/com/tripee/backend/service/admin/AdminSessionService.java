package com.tripee.backend.service.admin;

import com.tripee.backend.repository.admin.AdminAccounts;
import com.tripee.backend.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

@Service
@Profile("postgres")
public class AdminSessionService {
  private final AdminAccounts accounts;
  private final Clock clock;
  private final ObjectProvider<EditLockService> locks;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  private final String absentAccountHash = passwords.encode("unusable-fallback-password");
  private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

  public AdminSessionService(AdminAccounts accounts, Clock clock, ObjectProvider<EditLockService> locks) {
    this.accounts = accounts;
    this.clock = clock;
    this.locks = locks;
  }

  public AdminSession.View login(String loginId, String password, HttpServletRequest request, HttpServletResponse response) {
    if (loginId == null || loginId.isBlank() || loginId.length() > 64
        || password == null || password.isBlank() || password.length() > 1024) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "로그인 입력을 확인해 주세요.");
    }
    var account = accounts.find(loginId);
    String hash = account.map(AdminAccounts.Account::passwordHash).orElse(absentAccountHash);
    boolean matches;
    try {
      matches = passwords.matches(password, hash);
    } catch (IllegalArgumentException invalidEncoding) {
      matches = false;
    }
    if (!matches || account.isEmpty() || !account.get().active()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "로그인 정보를 확인해 주세요.");
    }
    var authenticated = account.orElseThrow();
    request.getSession();
    request.changeSessionId();
    var state = new AdminSession(authenticated.id(), authenticated.displayName(), clock.instant());
    request.getSession().setAttribute(AdminSession.ATTRIBUTE, state);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
        authenticated.id(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
    new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
    return state.view(clock.instant(), false);
  }

  public AdminSession.View current(HttpServletRequest request) {
    return state(request).view(clock.instant(), false);
  }

  public AdminSession.View activity(String event, HttpServletRequest request) {
    if (event == null || !List.of("INPUT", "CLICK", "NAVIGATION").contains(event)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "활동 종류를 확인해 주세요.");
    }
    return state(request).view(clock.instant(), true);
  }

  public void logout(HttpServletRequest request) {
    var service = locks.getIfAvailable();
    if (service != null) service.releaseSession(request.getSession(false));
    request.getSession(false).invalidate();
    SecurityContextHolder.clearContext();
  }

  private static AdminSession state(HttpServletRequest request) {
    return (AdminSession) request.getSession(false).getAttribute(AdminSession.ATTRIBUTE);
  }

}
