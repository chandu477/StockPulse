-- BE-02: persisted runtime settings, e.g. the active strategies (ADR-3).
create table app_settings (
  setting_key   varchar(64)  primary key,
  setting_value varchar(200) not null,
  updated_at    timestamp with time zone not null
);
