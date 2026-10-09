-- A document that reads like another kind (a receipt uploaded as a policy, or the reverse) keeps a
-- warning for the member; such a policy is left out of coordination of benefits.
ALTER TABLE documents ADD COLUMN warning VARCHAR(300);
