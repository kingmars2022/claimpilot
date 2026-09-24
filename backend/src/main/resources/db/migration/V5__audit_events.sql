-- What happened to a user's account and data, and when. Values from documents are never stored
-- here: only the action, what it applied to, and a short description.
CREATE TABLE audit_events (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    action       VARCHAR(40)  NOT NULL,
    target_type  VARCHAR(20),
    target_id    VARCHAR(64),
    detail       VARCHAR(300),
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_audit_events_user ON audit_events (user_id, created_at DESC);
