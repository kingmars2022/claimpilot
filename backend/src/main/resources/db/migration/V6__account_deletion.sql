-- Deleting an account touches PostgreSQL, MongoDB, the vector store and the file storage, which
-- cannot share one transaction. The request is recorded first; the account stops working at once,
-- and a background job finishes any deletion that was interrupted.
ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMPTZ;
