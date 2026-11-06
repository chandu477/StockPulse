package com.stockpulse.service;

import com.stockpulse.entity.AppSetting;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persisted, in-memory-cached runtime settings (ADR-3). Reads are lock-free; writes go to the
 * app_settings table and the cache, so a change takes effect on the next call with no restart.
 */
@Component
public class RuntimeSettings {
  private final AppSettingRepository repository;
  private final Clock clock;
  private final Map<String, String> cache = new ConcurrentHashMap<>();

  public RuntimeSettings(AppSettingRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public String get(String key, String defaultValue) {
    return cache.computeIfAbsent(
        key, k -> repository.findById(k).map(AppSetting::getValue).orElse(defaultValue));
  }

  @Transactional
  public void put(String key, String value) {
    Instant now = Instant.now(clock);
    AppSetting setting =
        repository
            .findById(key)
            .map(s -> {
              s.update(value, now);
              return s;
            })
            .orElseGet(() -> new AppSetting(key, value, now));
    repository.save(setting);
    cache.put(key, value);
  }
}
