create table recommendation_jobs (
    id varchar(36) primary key,
    product_id varchar(40) not null,
    trigger_reason varchar(40) not null,
    status varchar(20) not null,
    attempts int not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null,
    completed_at timestamp null,
    last_error_code varchar(80) null,
    version bigint not null default 0,
    constraint fk_recommendation_job_product foreign key (product_id) references products(id),
    constraint chk_recommendation_job_status check (status in ('PENDING','RUNNING','COMPLETED','FAILED')),
    constraint chk_recommendation_job_attempts check (attempts >= 0)
);
create index idx_recommendation_jobs_status_created on recommendation_jobs(status,created_at);
