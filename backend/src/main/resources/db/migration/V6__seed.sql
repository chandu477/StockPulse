-- BE-02: brief Addendum A seed data. Status is stored in its derived form (ADR-7): with no PENDING
-- suggestions, PRD-003 is ACTIVE (the brief lists PRICE_REVIEW_PENDING); PRD-006 is OUT_OF_STOCK.
-- Demo paths: PRD-003 (stock 8 < threshold 15) for INVENTORY_LOW; PRD-008 (velocity 15) for DEMAND_SPIKE.
insert into products (id, sku, name, category, current_price, stock_level, reorder_threshold, demand_velocity, status, created_at, updated_at) values
  ('PRD-001', 'SKU-ELEC-001', 'Wireless Earbuds Pro',       'ELECTRONICS', 79.99,  45, 20,  3, 'ACTIVE',       current_timestamp, current_timestamp),
  ('PRD-002', 'SKU-ELEC-002', 'USB-C Hub 7-Port',           'ELECTRONICS', 34.99, 120, 30,  1, 'ACTIVE',       current_timestamp, current_timestamp),
  ('PRD-003', 'SKU-APP-001',  'Organic Cotton T-Shirt',     'APPAREL',     24.99,   8, 15, 12, 'ACTIVE',       current_timestamp, current_timestamp),
  ('PRD-004', 'SKU-APP-002',  'Running Shorts - Navy',      'APPAREL',     39.99,  55, 20,  2, 'ACTIVE',       current_timestamp, current_timestamp),
  ('PRD-005', 'SKU-HOME-001', 'Ceramic Pour-Over Set',      'HOME',        49.99,  22, 10,  4, 'ACTIVE',       current_timestamp, current_timestamp),
  ('PRD-006', 'SKU-HOME-002', 'LED Desk Lamp - Dimmable',   'HOME',        59.99,   0, 15,  0, 'OUT_OF_STOCK', current_timestamp, current_timestamp),
  ('PRD-007', 'SKU-ELEC-003', 'Portable Charger 20K',       'ELECTRONICS', 44.99,  18, 25,  8, 'ACTIVE',       current_timestamp, current_timestamp),
  ('PRD-008', 'SKU-APP-003',  'Hoodie - Heather Grey',      'APPAREL',     54.99,  11, 12, 15, 'ACTIVE',       current_timestamp, current_timestamp);
