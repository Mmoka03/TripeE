package com.tripee.backend.controller.admin;

import com.tripee.backend.controller.admin.*;
import com.tripee.backend.service.admin.*;
import com.tripee.backend.repository.admin.*;
import com.tripee.backend.config.admin.*;


import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
  @GetMapping("/api/v1/admin/csrf")
  public TokenResponse csrf(CsrfToken csrf) {
    return new TokenResponse(csrf.getHeaderName(), csrf.getToken());
  }

  public record TokenResponse(String headerName, String token) {}
}
