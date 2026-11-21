package com.tripee.backend.config.admin;

import com.tripee.backend.controller.admin.*;
import com.tripee.backend.service.admin.*;
import com.tripee.backend.repository.admin.*;
import com.tripee.backend.config.admin.*;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

public final class AdminSessionExpiryFilter extends OncePerRequestFilter {
  private final Clock clock;
  private final ObjectMapper json;

  public AdminSessionExpiryFilter(Clock clock, ObjectMapper json) {
    this.clock = clock;
    this.json = json;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var session = request.getSession(false);
    if (session != null && session.getAttribute(AdminSession.ATTRIBUTE) instanceof AdminSession state
        && state.expired(clock.instant())) {
      session.invalidate();
      SecurityContextHolder.clearContext();
      AdminSecurity.writeError(json, request, response, HttpStatus.UNAUTHORIZED,
          "SESSION_EXPIRED", "세션이 만료되었습니다.");
      return;
    }
    chain.doFilter(request, response);
  }
}
