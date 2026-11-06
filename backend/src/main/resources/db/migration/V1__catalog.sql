-- BE-02: products. cost_price, margin_floor, supplier_id are Sprint 2 seams (ADR-6).
create sequence product_id_seq start with 100;

create table products (
  id                varchar(32)    primary key,
  sku               varchar(64)    not null,
  name              varchar(200)   not null,
  category          varchar(20)    not null,
  current_price     numeric(12, 2) not null,
  stock_level       integer        not null,
  reorder_threshold integer        not null,
  demand_velocity   integer        not null default 0,
  status            varchar(30)    not null,
  cost_price        numeric(12, 2),
  margin_floor      numeric(12, 2),
  supplier_id       varchar(64),
  version           bigint         not null default 0,
  created_at        timestamp with time zone not null,
  updated_at        timestamp with time zone not null,
  constraint uq_products_sku unique (sku),
  constraint ck_products_price check (current_price > 0),
  constraint ck_products_stock check (stock_level >= 0),
  constraint ck_products_threshold check (reorder_threshold >= 0),
  constraint ck_products_velocity check (demand_velocity >= 0)
);

create index idx_products_category on products (category);
create index idx_products_status on products (status);
