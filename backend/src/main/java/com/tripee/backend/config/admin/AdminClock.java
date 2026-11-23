package com.tripee.backend.config.admin;

import com.tripee.backend.controller.admin.*;
import com.tripee.backend.service.admin.*;
import com.tripee.backend.repository.admin.*;
import com.tripee.backend.config.admin.*;


import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AdminClock {
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
