-- V2__add_idempotency.sql
-- Create idempotency table to cache responses for idempotent requests.

CREATE TABLE idempotency_record (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    operation VARCHAR(100) NOT NULL,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
