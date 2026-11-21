package com.tripee.backend.admin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

@TestConfiguration(proxyBeanMethods = false)
class ContractCaptureConfiguration {
  @Bean
  MockMvcBuilderCustomizer captureContractResponses(ObjectMapper json) {
    return builder -> builder.alwaysDo(result -> {
      String output = System.getProperty("tripee.contract.responses");
      if (output == null) return;
      var request = result.getRequest();
      var response = result.getResponse();
      var record = json.createObjectNode();
      record.put("method", request.getMethod().toLowerCase(Locale.ROOT));
      record.put("path", request.getRequestURI().substring("/api/v1".length()));
      record.put("status", response.getStatus());
      record.put("contentType", response.getContentType());
      var headers = record.putObject("headers");
      for (String name : new String[]{"Cache-Control", "ETag", "Location"}) {
        if (response.getHeader(name) != null) headers.put(name.toLowerCase(Locale.ROOT), response.getHeader(name));
      }
      if (response.getContentType() != null && response.getContentType().contains("json")) {
        record.set("body", json.readTree(response.getContentAsByteArray()));
      }
      Files.writeString(Path.of(output), json.writeValueAsString(record) + "\n",
          StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    });
  }
}
