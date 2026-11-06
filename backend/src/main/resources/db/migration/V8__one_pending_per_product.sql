-- One PENDING suggestion per product and type (ADR-7 revision, 2026-11-06): pending_key becomes the
-- product id. Older duplicate PENDING rows are superseded first so the unique key holds.
update pricing_suggestions
   set status = 'SUPERSEDED', pending_key = null, decided_at = current_timestamp,
       decision_note = 'Superseded: one recommendation per product (V8).'
 where status = 'PENDING'
   and id not in (select max(id) from pricing_suggestions where status = 'PENDING' group by product_id);
update pricing_suggestions set pending_key = product_id where status = 'PENDING';

update reorder_suggestions
   set status = 'SUPERSEDED', pending_key = null, decided_at = current_timestamp,
       decision_note = 'Superseded: one recommendation per product (V8).'
 where status = 'PENDING'
   and id not in (select max(id) from reorder_suggestions where status = 'PENDING' group by product_id);
update reorder_suggestions set pending_key = product_id where status = 'PENDING';
