package com.tripee.backend;

import org.junit.jupiter.api.Tag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class TripeEApplicationTest {
  @Test
  void startsTheWebServer(@LocalServerPort int port) {
    assertTrue(port > 0);
  }
}
