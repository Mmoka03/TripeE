package com.tripee.backend.admin;

import com.tripee.backend.common.exception.ApiException;
import com.tripee.backend.controller.admin.PhotoFileController;
import com.tripee.backend.service.admin.AdminSession;
import com.tripee.backend.service.admin.PhotoFileService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("controller")
class PhotoFileControllerTest {
  private final PhotoFileService photos = mock(PhotoFileService.class);
  private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
  private final PhotoFileController controller = new PhotoFileController(photos, Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void rejectsMalformedAndOverflowingIdsBeforeAccessingStorage() {
    for (String id : List.of("0", "01", "-1", "9223372036854775808")) {
      assertThatThrownBy(() -> controller.image(id, new MockHttpServletRequest()))
          .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("MALFORMED_REQUEST"));
    }
    verifyNoInteractions(photos);
  }

  @Test
  void treatsOnlyMatchingUnexpiredAuthenticatedSessionAsAdministrator() {
    when(photos.read(anyLong(), anyBoolean())).thenReturn(new PhotoFileService.Image(new byte[]{1}, MediaType.IMAGE_PNG));
    var request = new MockHttpServletRequest();
    controller.image("1", request);
    verify(photos).read(1, false);
    var session = new MockHttpSession();
    request.setSession(session);
    session.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("7", "A", now));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("8", null, List.of()));
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    controller.image("2", request);
    verify(photos).read(2, false);
    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("7", null, List.of()));
    assertThat(controller.image("3", request).getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
    verify(photos).read(3, true);
    session.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("7", "A", now.minusSeconds(3600)));
    controller.image("4", request);
    verify(photos).read(4, false);
  }
}
