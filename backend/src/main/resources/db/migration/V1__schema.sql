-- ClaimPilot schema. Every row that holds personal data belongs to one user and is removed with
-- that user (ON DELETE CASCADE), so "delete my account" leaves nothing behind in PostgreSQL.

CREATE TABLE users (
    id             BIGSERIAL    PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    display_name   VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Entered once, reused on every claim form. No social insurance number is ever stored.
CREATE TABLE profiles (
    user_id        BIGINT       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    full_name      VARCHAR(150),
    date_of_birth  DATE,
    street         VARCHAR(200),
    city           VARCHAR(100),
    province       VARCHAR(50),
    postal_code    VARCHAR(20),
    phone          VARCHAR(40),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Uploaded files: insurance policies (indexed for questions) and receipts.
CREATE TABLE documents (
    id             UUID         PRIMARY KEY,
    owner_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind           VARCHAR(20)  NOT NULL,   -- POLICY, RECEIPT
    file_name      VARCHAR(255) NOT NULL,
    content_type   VARCHAR(255),
    size_bytes     BIGINT       NOT NULL,
    storage_key    VARCHAR(512) NOT NULL,
    status         VARCHAR(20)  NOT NULL,   -- UPLOADED, PROCESSING, READY, FAILED
    chunk_count    INTEGER,
    error_message  TEXT,
    created_at     TIMESTAMPTZ  NOT NULL,
    processed_at   TIMESTAMPTZ
);

CREATE INDEX idx_documents_owner_kind ON documents (owner_id, kind, created_at DESC);

-- Key facts read from a document by the model, with the exact quote that supports each one.
-- "verified" means the quote was found in the document text by code, not just claimed by the model.
CREATE TABLE document_facts (
    id           BIGSERIAL   PRIMARY KEY,
    document_id  UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    fact_key     VARCHAR(50) NOT NULL,
    value        TEXT        NOT NULL,
    quote        TEXT,
    page         INTEGER,
    verified     BOOLEAN     NOT NULL,
    UNIQUE (document_id, fact_key)
);

-- Which canonical data item each field of a claim form holds. Worked out by the model once per
-- form version (identified by its SHA-256) and reused afterwards without another model call.
CREATE TABLE form_field_mappings (
    id               BIGSERIAL    PRIMARY KEY,
    template_sha256  CHAR(64)     NOT NULL,
    pdf_field_name   VARCHAR(200) NOT NULL,
    data_key         VARCHAR(50)  NOT NULL,
    label            TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (template_sha256, pdf_field_name)
);

CREATE TABLE claim_drafts (
    id               UUID         PRIMARY KEY,
    owner_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    claim_type       VARCHAR(40)  NOT NULL,
    policy_id        UUID         REFERENCES documents (id) ON DELETE SET NULL,  -- plan being claimed on
    other_policy_id  UUID         REFERENCES documents (id) ON DELETE SET NULL,  -- plan that paid first
    receipt_id       UUID         REFERENCES documents (id) ON DELETE SET NULL,
    relationship     VARCHAR(30)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_claim_drafts_owner ON claim_drafts (owner_id, updated_at DESC);

-- One pre-filled value per canonical data item, with where it came from.
CREATE TABLE claim_draft_fields (
    id                  BIGSERIAL    PRIMARY KEY,
    draft_id            UUID         NOT NULL REFERENCES claim_drafts (id) ON DELETE CASCADE,
    data_key            VARCHAR(50)  NOT NULL,
    value               TEXT,
    source_type         VARCHAR(20)  NOT NULL,   -- POLICY, OTHER_POLICY, RECEIPT, PROFILE, CLAIM_SETUP, CALCULATED, USER, MISSING
    source_document_id  UUID,
    source_label        TEXT,
    source_page         INTEGER,
    source_quote        TEXT,
    verified            BOOLEAN      NOT NULL,
    reviewed            BOOLEAN      NOT NULL DEFAULT false,
    UNIQUE (draft_id, data_key)
);
