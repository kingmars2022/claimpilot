-- Claims can be filled on different forms: a built-in one or a fillable PDF the user uploaded.
ALTER TABLE claim_drafts ADD COLUMN form_key VARCHAR(100) NOT NULL DEFAULT 'builtin:cedarview-secondary';
