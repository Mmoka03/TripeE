package com.tripee.backend.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("postgres")
public class QuerydslConfig {
  @Bean
  JPAQueryFactory queryFactory(EntityManager entities) {
    return new JPAQueryFactory(entities);
  }
}
