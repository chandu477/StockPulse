package com.stockpulse.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
  private final String[] origins;

  public CorsConfig(@Value("${stockpulse.cors.allowed-origins}") String origins) {
    this.origins = origins.split("\\s*,\\s*");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(origins)
        .allowedMethods("GET", "POST", "PUT", "PATCH", "OPTIONS")
        .exposedHeaders("X-Trace-Id", "X-Deduplicated", "Location");
  }
}
