package com.tripee.backend.admin;

import com.tripee.backend.service.admin.AdminSession;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.tripee.backend.service.admin.PhotoCleanupService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("postgres")
@Tag("integration")
@ActiveProfiles("postgres")
@SpringBootTest
@org.springframework.context.annotation.Import(ContractCaptureConfiguration.class)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TRIPEE_TEST_ISOLATED_DB", matches = "true")
class DraftWorkflowPostgresTest extends com.tripee.backend.PostgresIntegrationTestBase {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired PhotoCleanupService cleanup;
  private static final java.nio.file.Path PHOTO_ROOT = createPhotoRoot();

  private static java.nio.file.Path createPhotoRoot() {
    try {
      return java.nio.file.Files.createTempDirectory("tripee-photo-test-");
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry registry) {
    registry.add("tripee.photos.directory", PHOTO_ROOT::toString);
  }

  @org.junit.jupiter.api.AfterAll
  static void removeEmptyPhotoRoot() throws Exception {
    try (var files = java.nio.file.Files.list(PHOTO_ROOT)) {
      for (var file : files.toList()) java.nio.file.Files.deleteIfExists(file);
    }
    java.nio.file.Files.deleteIfExists(PHOTO_ROOT);
  }

  @Test
  void createsLocksSavesAndRejectsStaleDraftWithoutLosingData() throws Exception {
    mvc.perform(get("/api/v1/cities")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/tags")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/search").param("q", "교토")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/cities").param("countryCode", "invalid")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/admin/session")).andExpect(status().isUnauthorized());
    var session = new MockHttpSession();
    session.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("1", "A", Instant.now()));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("1", null,
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    var csrfResponse = mvc.perform(get("/api/v1/admin/csrf").session(session)).andExpect(status().isOk()).andReturn();
    String csrf = json.readTree(csrfResponse.getResponse().getContentAsByteArray()).get("token").asString();

    mvc.perform(post("/api/v1/admin/cities").session(session).header("X-CSRF-TOKEN", csrf)
        .contentType(MediaType.TEXT_PLAIN).content("{}"))
        .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE")).andExpect(jsonPath("$.traceId").isNotEmpty());
    var created = mvc.perform(post("/api/v1/admin/cities").session(session).header("X-CSRF-TOKEN", csrf)
        .contentType(MediaType.APPLICATION_JSON).content("""
          {"countryId":"1","nameKo":null,"nameEn":null,"coordinates":null,"tagline":null}
          """)).andExpect(status().isCreated()).andReturn();
    String cityId = json.readTree(created.getResponse().getContentAsByteArray()).at("/city/id").asString();
    String path = "/api/v1/admin/cities/" + cityId;
    var acquired = mvc.perform(post(path + "/edit-lock").session(session).header("X-CSRF-TOKEN", csrf)
        .contentType(MediaType.APPLICATION_JSON).content("""
          {"editorInstanceId":"8c460af2-46de-46fa-aae5-53c47710c2ce"}
          """)).andExpect(status().isCreated()).andReturn();
    String lease = json.readTree(acquired.getResponse().getContentAsByteArray()).get("leaseToken").asString();
    var sessionB = new MockHttpSession();
    sessionB.setAttribute(AdminSession.ATTRIBUTE, new AdminSession("2", "B", Instant.now()));
    var contextB = SecurityContextHolder.createEmptyContext();
    contextB.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("2", null,
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    sessionB.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contextB);
    mvc.perform(get(path + "/draft").session(sessionB).header("X-Edit-Lock-Token", lease))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EDIT_LOCK_NOT_OWNED"));
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
    var file = new MockMultipartFile("file", "test.png", "image/png", bytes.toByteArray());
    var metadata = new MockMultipartFile("metadata", "", "application/json", """
        {"sourceUrl":"https://example.org/image","creatorName":null,"licenseName":"CC0",
         "licenseUrl":null,"usageNote":null,"rightsConfirmed":true}
        """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    mvc.perform(multipart(path + "/photo-assets").file(file).session(session)
        .header("X-CSRF-TOKEN", csrf).header("X-Edit-Lock-Token", lease))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST")).andExpect(jsonPath("$.traceId").isNotEmpty());
    var invalidFile = new MockMultipartFile("file", "bad.png", "image/png", new byte[]{0, 1, 2, 3});
    mvc.perform(multipart(path + "/photo-assets").file(invalidFile).file(metadata)
        .session(session).header("X-CSRF-TOKEN", csrf).header("X-Edit-Lock-Token", lease))
        .andExpect(status().isUnsupportedMediaType());
    var uploaded = mvc.perform(multipart(path + "/photo-assets").file(file).file(metadata)
        .session(session).header("X-CSRF-TOKEN", csrf).header("X-Edit-Lock-Token", lease))
        .andExpect(status().isCreated()).andReturn();
    String imageUrl = json.readTree(uploaded.getResponse().getContentAsByteArray()).get("imageUrl").asString();
    String photoId = json.readTree(uploaded.getResponse().getContentAsByteArray()).get("id").asString();
    mvc.perform(get(path + "/photo-assets").session(session).header("X-Edit-Lock-Token", lease))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(photoId))
        .andExpect(jsonPath("$.items[0].rightsCheckedAt").isNotEmpty());
    mvc.perform(get(imageUrl)).andExpect(status().isNotFound());
    mvc.perform(get(imageUrl).session(session)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
    var original = mvc.perform(get(path + "/draft").session(session).header("X-Edit-Lock-Token", lease))
        .andExpect(status().isOk()).andReturn();
    String etag = original.getResponse().getHeader("ETag");
    var initialDraft = json.readTree(original.getResponse().getContentAsByteArray());
    String initialExpected = "{\"expectedRevisionId\":\"" + initialDraft.get("revisionId").asString()
        + "\",\"expectedEditVersion\":\"0\"}";
    mvc.perform(post(path + "/draft/preview").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).contentType(MediaType.APPLICATION_JSON).content(initialExpected))
        .andExpect(status().isOk()).andExpect(jsonPath("$.canPublish").value(false));
    mvc.perform(post(path + "/draft/publications").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).contentType(MediaType.APPLICATION_JSON).content(initialExpected))
        .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PUBLICATION_NOT_READY"));
    String body = """
        {"nameKo":"교토","nameEn":"Kyoto","coordinates":{"latitude":35,"longitude":135},
         "tagline":"여행","tagIds":[],"photos":[],"contentItems":[],"safetyDraft":null}
        """;
    var saved = mvc.perform(put(path + "/draft").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).header("If-Match", etag)
        .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.nameKo").value("교토"))
        .andExpect(jsonPath("$.editVersion").value("1")).andReturn();
    assertThat(saved.getResponse().getHeader("ETag")).isNotEqualTo(etag);
    String currentEtag = saved.getResponse().getHeader("ETag");
    mvc.perform(post(path + "/draft/publications").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).contentType(MediaType.APPLICATION_JSON).content(initialExpected))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRAFT_VERSION_MISMATCH"));
    mvc.perform(delete(path + "/draft").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).header("If-Match", etag))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(put(path + "/draft").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isPreconditionRequired());
    String invalidTag = """
        {"nameKo":"덮어쓰기","nameEn":"Kyoto","coordinates":{"latitude":35,"longitude":135},
         "tagline":"여행","tagIds":["9223372036854775807"],"photos":[],"contentItems":[],"safetyDraft":null}
        """;
    mvc.perform(put(path + "/draft").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).header("If-Match", currentEtag)
        .contentType(MediaType.APPLICATION_JSON).content(invalidTag))
        .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mvc.perform(put(path + "/draft").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).header("If-Match", etag)
        .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.code").value("RESOURCE_VERSION_MISMATCH"));
    mvc.perform(get(path + "/draft").session(session).header("X-Edit-Lock-Token", lease))
        .andExpect(status().isOk()).andExpect(jsonPath("$.editVersion").value("1"))
        .andExpect(jsonPath("$.nameKo").value("교토"));
    var safety = mvc.perform(get(path + "/safety").session(session)).andExpect(status().isOk())
        .andExpect(jsonPath("$.revisionId").isEmpty()).andReturn();
    String safetyEtag = safety.getResponse().getHeader("ETag");
    // Publication validation happens after a revision is inserted; failure must roll the transaction back.
    mvc.perform(put(path + "/safety").session(session).header("X-CSRF-TOKEN", csrf)
        .header("If-Match", safetyEtag).contentType(MediaType.APPLICATION_JSON)
        .content("{\"reviewState\":\"PARTIAL\",\"items\":[]}"))
        .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mvc.perform(get(path + "/safety").session(session)).andExpect(status().isOk())
        .andExpect(header().string("ETag", safetyEtag)).andExpect(jsonPath("$.revisionId").isEmpty());
    var publishedSafety = mvc.perform(put(path + "/safety").session(session).header("X-CSRF-TOKEN", csrf)
        .header("If-Match", safetyEtag).contentType(MediaType.APPLICATION_JSON)
        .content("{\"reviewState\":\"UNVERIFIED\",\"items\":[]}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.revisionId").isNotEmpty()).andReturn();
    String safetyRevision = json.readTree(publishedSafety.getResponse().getContentAsByteArray()).get("revisionId").asString();
    mvc.perform(put(path + "/safety").session(session).header("X-CSRF-TOKEN", csrf)
        .header("If-Match", safetyEtag).contentType(MediaType.APPLICATION_JSON)
        .content("{\"reviewState\":\"UNVERIFIED\",\"items\":[]}"))
        .andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.code").value("SAFETY_VERSION_MISMATCH"));
    String conflictingSafety = """
        {"nameKo":"롤백확인","nameEn":"Kyoto","coordinates":{"latitude":35,"longitude":135},
         "tagline":"변경","tagIds":[],"photos":[],"contentItems":[],
         "safetyDraft":{"reviewState":"UNVERIFIED","items":[]}}
        """;
    mvc.perform(put(path + "/draft").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).header("If-Match", currentEtag)
        .contentType(MediaType.APPLICATION_JSON).content(conflictingSafety))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAFETY_CHANGED"));
    mvc.perform(get(path + "/draft").session(session).header("X-Edit-Lock-Token", lease))
        .andExpect(status().isOk()).andExpect(header().string("ETag", currentEtag))
        .andExpect(jsonPath("$.nameKo").value("교토")).andExpect(jsonPath("$.tagline").value("여행"));
    var sourceResponse = mvc.perform(post("/api/v1/admin/source-documents").session(session)
        .header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
        .content("{\"url\":\"https://example.org/source\",\"title\":\"자료\",\"publisher\":null}"))
        .andExpect(status().isCreated()).andReturn();
    String sourceId = json.readTree(sourceResponse.getResponse().getContentAsByteArray()).get("id").asString();
    String aiRequest = "{\"expectedRevisionId\":\"" + initialDraft.get("revisionId").asString()
        + "\",\"expectedEditVersion\":\"1\",\"sources\":[{\"sourceDocumentId\":\"" + sourceId
        + "\",\"text\":\"검토할 자료\"}]}";
    mvc.perform(post(path + "/draft/experience-suggestions").session(session)
        .header("X-Edit-Lock-Token", lease).contentType(MediaType.APPLICATION_JSON).content(aiRequest))
        .andExpect(status().isForbidden());
    mvc.perform(post(path + "/draft/experience-suggestions").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).contentType(MediaType.APPLICATION_JSON).content(aiRequest))
        .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AI_UPSTREAM_ERROR"));
    mvc.perform(get(path + "/draft").session(session).header("X-Edit-Lock-Token", lease))
        .andExpect(status().isOk()).andExpect(header().string("ETag", currentEtag));
    mvc.perform(get("/api/v1/admin/source-documents/" + sourceId).session(session))
        .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("자료"));
    mvc.perform(get("/api/v1/admin/source-documents").session(session).param("q", "자료"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(sourceId));
    String itemTemplate = """
        {"id":null,"kind":"%s","title":"제목","body":"본문","displayOrder":1,"isSelected":true,
         "sources":[{"sourceDocumentId":"%s","evidenceNote":"확인","reviewAction":"CONFIRM"}]}
        """;
    String ready = """
        {"nameKo":"교토","nameEn":"Kyoto","coordinates":{"latitude":35,"longitude":135},
         "tagline":"여행","tagIds":[],"photos":[{"photoAssetId":"%s","displayOrder":1,
         "altText":"교토","caption":null}],"contentItems":[%s,%s,%s,%s],"safetyDraft":null}
        """.formatted(photoId, itemTemplate.formatted("EXPERIENCE", sourceId),
        itemTemplate.formatted("VISIT_PERIOD", sourceId), itemTemplate.formatted("ARRIVAL", sourceId),
        itemTemplate.formatted("LOCAL_TRANSPORT", sourceId));
    var readyResponse = mvc.perform(put(path + "/draft").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).header("If-Match", currentEtag)
        .contentType(MediaType.APPLICATION_JSON).content(ready))
        .andExpect(status().isOk()).andExpect(jsonPath("$.editVersion").value("2")).andReturn();
    var draft = json.readTree(readyResponse.getResponse().getContentAsByteArray());
    var resubmit = (tools.jackson.databind.node.ObjectNode) json.readTree(ready);
    for (var inputItem : resubmit.get("contentItems")) {
      for (var savedItem : draft.get("contentItems")) {
        if (inputItem.get("kind").asString().equals(savedItem.get("kind").asString())) {
          ((tools.jackson.databind.node.ObjectNode) inputItem).set("id", savedItem.get("id"));
          ((tools.jackson.databind.node.ObjectNode) inputItem.get("sources").get(0)).put("reviewAction", "KEEP");
        }
      }
    }
    var updated = mvc.perform(put(path + "/draft").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).header("If-Match", readyResponse.getResponse().getHeader("ETag"))
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(resubmit)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.editVersion").value("3"))
        .andExpect(jsonPath("$.contentItems[0].sources[0].checkedAt").isNotEmpty()).andReturn();
    String updatedEtag = updated.getResponse().getHeader("ETag");
    ((tools.jackson.databind.node.ObjectNode) resubmit.get("contentItems").get(0).get("sources").get(0))
        .put("sourceDocumentId", "9223372036854775807");
    resubmit.put("nameKo", "저장되면 안 됨");
    mvc.perform(put(path + "/draft").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).header("If-Match", updatedEtag)
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(resubmit)))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(get(path + "/draft").session(session).header("X-Edit-Lock-Token", lease))
        .andExpect(status().isOk()).andExpect(header().string("ETag", updatedEtag))
        .andExpect(jsonPath("$.nameKo").value("교토"));
    String review = "{\"expectedRevisionId\":\"" + draft.get("revisionId").asString()
        + "\",\"expectedEditVersion\":\"3\",\"reviewedPublicSafetyRevisionId\":\"" + safetyRevision
        + "\",\"safety\":{\"reviewState\":\"UNVERIFIED\",\"items\":[]}}";
    mvc.perform(post(path + "/draft/safety-reviews").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).contentType(MediaType.APPLICATION_JSON).content(review))
        .andExpect(status().isOk()).andExpect(jsonPath("$.editVersion").value("4"))
        .andExpect(jsonPath("$.safetyDraft.reviewState").value("UNVERIFIED"));
    String expected = "{\"expectedRevisionId\":\"" + draft.get("revisionId").asString()
        + "\",\"expectedEditVersion\":\"4\"}";
    mvc.perform(post(path + "/draft/preview").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).contentType(MediaType.APPLICATION_JSON).content(expected))
        .andExpect(status().isOk()).andExpect(jsonPath("$.canPublish").value(true));
    mvc.perform(post(path + "/draft/publications").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).contentType(MediaType.APPLICATION_JSON).content(expected))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.visibility").value("PUBLIC"))
        .andExpect(header().string("Location", path + "/revisions/" + draft.get("revisionId").asString()));
    mvc.perform(get(path + "/revisions/" + draft.get("revisionId").asString()).session(session))
        .andExpect(status().isOk()).andExpect(jsonPath("$.cityId").value(cityId))
        .andExpect(jsonPath("$.snapshot").isNotEmpty());
    var events = mvc.perform(get("/api/v1/admin/audit-events").param("cityRefId", cityId).session(session))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].action").value("PUBLISH")).andReturn();
    String eventId = json.readTree(events.getResponse().getContentAsByteArray()).at("/items/0/id").asString();
    mvc.perform(get("/api/v1/admin/audit-events/" + eventId).session(session))
        .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("PUBLISH"))
        .andExpect(jsonPath("$.afterData").isNotEmpty());
    mvc.perform(get(imageUrl)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
    var reacquired = mvc.perform(post(path + "/edit-lock").session(session).header("X-CSRF-TOKEN", csrf)
        .contentType(MediaType.APPLICATION_JSON).content("""
          {"editorInstanceId":"8c460af2-46de-46fa-aae5-53c47710c2ce"}
          """)).andExpect(status().isCreated()).andReturn();
    lease = json.readTree(reacquired.getResponse().getContentAsByteArray()).get("leaseToken").asString();
    var publicCity = mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn();
    var hidden = mvc.perform(patch(path).session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).header("If-Match", publicCity.getResponse().getHeader("ETag"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"HIDDEN\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("HIDDEN")).andReturn();
    mvc.perform(get("/api/v1/cities/" + cityId)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/admin/audit-events").param("cityRefId", cityId).session(session))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].action").value("UNPUBLISH"));
    mvc.perform(patch(path).session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).header("If-Match", hidden.getResponse().getHeader("ETag"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PREPARING\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("PREPARING"));
    mvc.perform(get("/api/v1/admin/audit-events").param("cityRefId", cityId).session(session))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].action").value("SET_VISIBILITY"));
    var createdDraft = mvc.perform(post(path + "/draft").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease)).andExpect(status().isCreated()).andReturn();
    mvc.perform(get(path + "/draft").session(session).header("X-Edit-Lock-Token", lease))
        .andExpect(status().isOk()).andExpect(jsonPath("$.nameKo").value("교토"));
    mvc.perform(delete(path + "/draft").session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).header("If-Match", createdDraft.getResponse().getHeader("ETag")))
        .andExpect(status().isNoContent());
    mvc.perform(get(path + "/draft").session(session).header("X-Edit-Lock-Token", lease))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DRAFT_NOT_FOUND"));
    var city = mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn();
    mvc.perform(delete(path).session(session).header("X-CSRF-TOKEN", csrf)
        .header("X-Edit-Lock-Token", lease).header("If-Match", city.getResponse().getHeader("ETag")))
        .andExpect(status().isNoContent());
    mvc.perform(get(path).session(session)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/admin/audit-events").param("cityRefId", cityId).session(session))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].action").value("DELETE"));
    cleanup.cleanup();
    try (var files = java.nio.file.Files.list(PHOTO_ROOT)) {
      assertThat(files).isEmpty();
    }
  }
}
