-- Demo persona: a clothing supplier pricing in Indian rupees (INR, 2026-11-06).
-- All products are APPAREL (the brief's category enum is kept), so "peer average" = other clothing.
-- Demo paths: PRD-003 T-Shirt (stock 8 < 15) -> INVENTORY_LOW on the next sale;
--             PRD-008 Hoodie (velocity 8; spike when > 3x peers = 10.3) -> DEMAND_SPIKE after ~3 sales.
update products set sku = 'SKU-TOP-001', name = 'Men''s Linen Shirt - White',        category = 'APPAREL', current_price = 1899.00, cost_price =  950.00, stock_level =  45, reorder_threshold = 20, demand_velocity = 3, status = 'ACTIVE'       where id = 'PRD-001';
update products set sku = 'SKU-BTM-001', name = 'Slim Fit Denim Jeans - Indigo',     category = 'APPAREL', current_price = 2499.00, cost_price = 1250.00, stock_level = 120, reorder_threshold = 30, demand_velocity = 1, status = 'ACTIVE'       where id = 'PRD-002';
update products set sku = 'SKU-TOP-002', name = 'Organic Cotton T-Shirt - Black',    category = 'APPAREL', current_price =  799.00, cost_price =  360.00, stock_level =   8, reorder_threshold = 15, demand_velocity = 6, status = 'ACTIVE'       where id = 'PRD-003';
update products set sku = 'SKU-BTM-002', name = 'Running Shorts - Navy',             category = 'APPAREL', current_price = 1299.00, cost_price =  620.00, stock_level =  55, reorder_threshold = 20, demand_velocity = 2, status = 'ACTIVE'       where id = 'PRD-004';
update products set sku = 'SKU-ETH-001', name = 'Cotton Kurta - Indigo Block Print', category = 'APPAREL', current_price = 1599.00, cost_price =  780.00, stock_level =  22, reorder_threshold = 10, demand_velocity = 4, status = 'ACTIVE'       where id = 'PRD-005';
update products set sku = 'SKU-OUT-001', name = 'Quilted Winter Jacket - Olive',     category = 'APPAREL', current_price = 4999.00, cost_price = 2600.00, stock_level =   0, reorder_threshold = 15, demand_velocity = 0, status = 'OUT_OF_STOCK' where id = 'PRD-006';
update products set sku = 'SKU-TOP-003', name = 'Polo T-Shirt - Maroon',             category = 'APPAREL', current_price =  999.00, cost_price =  470.00, stock_level =  18, reorder_threshold = 25, demand_velocity = 8, status = 'ACTIVE'       where id = 'PRD-007';
update products set sku = 'SKU-OUT-002', name = 'Pullover Hoodie - Heather Grey',    category = 'APPAREL', current_price = 1999.00, cost_price =  900.00, stock_level =  40, reorder_threshold = 12, demand_velocity = 8, status = 'ACTIVE'       where id = 'PRD-008';

-- Prices and stock were rewritten, so any open suggestion is stale.
update pricing_suggestions set status = 'SUPERSEDED', pending_key = null, decided_at = current_timestamp,
       decision_note = 'Superseded: catalog re-seeded in INR (V9).' where status = 'PENDING';
update reorder_suggestions set status = 'SUPERSEDED', pending_key = null, decided_at = current_timestamp,
       decision_note = 'Superseded: catalog re-seeded in INR (V9).' where status = 'PENDING';
