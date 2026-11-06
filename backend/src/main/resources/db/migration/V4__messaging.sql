-- BE-02: transactional outbox and idempotent-consumer table (ADR-5).
create table outbox_events (
  id           uuid         primary key,
  aggregate_id varchar(32)  not null,
  event_type   varchar(40)  not null,
  payload      varchar(2000) not null,
  trace_id     varchar(64),
  status       varchar(20)  not null,
  attempts     integer      not null default 0,
  last_error   varchar(500),
  created_at   timestamp with time zone not null,
  published_at timestamp with time zone
);
create index idx_outbox_status_created on outbox_events (status, created_at);

create table processed_messages (
  message_id   varchar(64) primary key,
  processed_at timestamp with time zone not null
);
