alter table products add column cost_price decimal(12,2) null;
alter table products add column supplier_id varchar(80) null;
alter table pricing_suggestions add column version bigint not null default 0;
alter table reorder_suggestions add column version bigint not null default 0;
alter table reorder_suggestions add column suggested_lead_time_days int not null default 7;

alter table pricing_suggestions add constraint chk_pricing_confidence check (confidence >= 0 and confidence <= 1);
alter table pricing_suggestions add constraint chk_pricing_positive check (current_price > 0 and recommended_price > 0);
alter table reorder_suggestions add constraint chk_reorder_confidence check (confidence >= 0 and confidence <= 1);
alter table reorder_suggestions add constraint chk_reorder_quantity check (recommended_quantity > 0 and suggested_lead_time_days > 0);

create index idx_ps_product_trigger_status on pricing_suggestions(product_id, trigger_reason, status);
create index idx_rs_product_trigger_status on reorder_suggestions(product_id, trigger_reason, status);
