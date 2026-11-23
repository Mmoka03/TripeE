package com.tripee.backend.controller.admin;

import com.tripee.backend.service.admin.AdminSession;
import com.tripee.backend.service.admin.AdminSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("postgres")
public class AdminSessionController {
  private final AdminSessionService sessions;

  public AdminSessionController(AdminSessionService sessions) {
    this.sessions = sessions;
  }

  @PostMapping("/api/v1/admin/session")
  public AdminSession.View login(@RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
    return sessions.login(input.loginId(), input.password(), request, response);
  }

  @GetMapping("/api/v1/admin/session")
  public AdminSession.View current(HttpServletRequest request) {
    return sessions.current(request);
  }

  @PostMapping("/api/v1/admin/session/activity")
  public AdminSession.View activity(@RequestBody Activity input, HttpServletRequest request) {
    return sessions.activity(input.event(), request);
  }

  @DeleteMapping("/api/v1/admin/session")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request) {
    sessions.logout(request);
  }

  public record Login(String loginId, String password) {}
  public record Activity(String event) {}
}
