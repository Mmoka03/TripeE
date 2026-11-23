package com.tripee.backend.config.admin;

import com.tripee.backend.controller.admin.*;
import com.tripee.backend.service.admin.*;
import com.tripee.backend.repository.admin.*;
import com.tripee.backend.config.admin.*;


import com.tripee.backend.common.exception.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
@Import(AdminClock.class)
public class AdminSecurity {
  @Bean
  public SecurityFilterChain adminEndpoints(HttpSecurity http, ObjectMapper json, Clock clock) throws Exception {
    http.securityMatcher("/api/v1/admin/**")
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/api/v1/admin/csrf").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/admin/session").permitAll()
            .anyRequest().authenticated())
        .requestCache(cache -> cache.disable())
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((req, res, ex) -> writeError(json, req, res, HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED", "관리자 로그인이 필요합니다."))
            .accessDeniedHandler((req, res, ex) -> {
              if (ex instanceof CsrfException) {
                writeError(json, req, res, HttpStatus.FORBIDDEN, "CSRF_INVALID", "CSRF 토큰을 다시 받아 주세요.");
              } else {
                writeError(json, req, res, HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.");
              }
            }));
    http.addFilterAfter(new AdminSessionExpiryFilter(clock, json), SecurityContextHolderFilter.class);
    return http.build();
  }

  public static void writeError(ObjectMapper json, HttpServletRequest req, HttpServletResponse res,
                                 HttpStatus status, String code, String detail) throws IOException {
    res.setStatus(status.value());
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(res.getOutputStream(), ApiProblems.problem(status, code, detail, req));
  }
}
