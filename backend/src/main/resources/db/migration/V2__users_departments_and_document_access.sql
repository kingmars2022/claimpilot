-- Phase 2: users, departments and per-document visibility.

CREATE TABLE departments (
    id    BIGSERIAL    PRIMARY KEY,
    code  VARCHAR(32)  NOT NULL UNIQUE,   -- stable key stored in vector metadata, e.g. "HR"
    name  VARCHAR(100) NOT NULL
);

CREATE TABLE users (
    id             BIGSERIAL    PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    display_name   VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(30)  NOT NULL,   -- EMPLOYEE, KNOWLEDGE_MANAGER, ADMIN
    department_id  BIGINT       REFERENCES departments (id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- A document with no rows here is visible to the whole company.
CREATE TABLE document_departments (
    document_id    UUID   NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    department_id  BIGINT NOT NULL REFERENCES departments (id),
    PRIMARY KEY (document_id, department_id)
);

-- Phase 1 chunks have no access list yet: mark them company-wide.
-- vector_store is created by Spring AI on first start, so it may not exist on a fresh database.
DO $$
BEGIN
    IF to_regclass('public.vector_store') IS NOT NULL THEN
        UPDATE vector_store
        SET metadata = jsonb_set(metadata::jsonb, '{access}', '["ALL"]'::jsonb)::json
        WHERE NOT (metadata::jsonb ? 'access');
    END IF;
END $$;
