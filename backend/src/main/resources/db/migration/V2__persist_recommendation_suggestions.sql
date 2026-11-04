create table recommendation_suggestions (
    id varchar(36) primary key,
    product_id varchar(40) not null,
    type varchar(20) not null,
    trigger_reason varchar(40) not null,
    status varchar(20) not null,
    recommended_price decimal(12,2),
    recommended_quantity int,
    confidence decimal(4,3) not null,
    reasoning varchar(2000) not null,
    strategy_name varchar(80) not null,
    created_at timestamp not null,
    reviewed_at timestamp null,
    version bigint not null default 0,
    constraint fk_recommendation_product foreign key (product_id) references products(id),
    constraint chk_recommendation_confidence check (confidence >= 0 and confidence <= 1),
    constraint chk_recommendation_status check (status in ('PENDING','ACCEPTED','REJECTED','FAILED','EXPIRED')),
    constraint chk_recommendation_price check (recommended_price is null or recommended_price > 0),
    constraint chk_recommendation_quantity check (recommended_quantity is null or recommended_quantity > 0),
    constraint chk_recommendation_not_empty check (recommended_price is not null or recommended_quantity is not null)
);

create index idx_suggestion_product_status on recommendation_suggestions(product_id, status);
create index idx_suggestion_created_at on recommendation_suggestions(created_at);
