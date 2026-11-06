-- Demo data for the margin display (UI ceiling) using the Sprint 2 cost_price seam (ADR-6).
-- New migration rather than editing V6 (applied migrations are never edited).
update products set cost_price = 41.00 where id = 'PRD-001';
update products set cost_price = 17.50 where id = 'PRD-002';
update products set cost_price = 11.20 where id = 'PRD-003';
update products set cost_price = 19.00 where id = 'PRD-004';
update products set cost_price = 24.50 where id = 'PRD-005';
update products set cost_price = 31.00 where id = 'PRD-006';
update products set cost_price = 23.40 where id = 'PRD-007';
update products set cost_price = 26.00 where id = 'PRD-008';
