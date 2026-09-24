-- The signature rule used to match "certif", which also blanked the certificate number fields.
-- Clear the stored mappings so each form is mapped again with the corrected rule.
DELETE FROM form_field_mappings;
