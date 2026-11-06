package com.stockpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_settings")
public class AppSetting {
  @Id
  @Column(name = "setting_key")
  private String key;

  @Column(name = "setting_value", nullable = false)
  private String value;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AppSetting() {}

  public AppSetting(String key, String value, Instant updatedAt) {
    this.key = key;
    this.value = value;
    this.updatedAt = updatedAt;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public void update(String newValue, Instant at) {
    this.value = newValue;
    this.updatedAt = at;
  }
}
