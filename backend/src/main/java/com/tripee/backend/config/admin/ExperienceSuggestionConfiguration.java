package com.tripee.backend.config.admin;

import com.tripee.backend.service.admin.ExperienceSuggestionProvider;
import com.tripee.backend.service.admin.UnavailableExperienceSuggestionProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("postgres")
public class ExperienceSuggestionConfiguration {
  @Bean
  @ConditionalOnMissingBean(ExperienceSuggestionProvider.class)
  ExperienceSuggestionProvider experienceSuggestionProvider() {
    return new UnavailableExperienceSuggestionProvider();
  }
}
