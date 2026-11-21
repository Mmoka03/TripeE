package com.tripee.backend;

import com.tripee.backend.config.admin.AdminSecurity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@Import(AdminSecurity.class)
public class TripeEApplication {
  public static void main(String[] args) {
    SpringApplication.run(TripeEApplication.class, args);
  }
}
