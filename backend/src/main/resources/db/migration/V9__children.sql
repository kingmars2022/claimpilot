-- Custody is set per child: a family can have children from two relationships, each with its own
-- other parent and custody. A child's name and birth date also go on the claim form as the patient.
CREATE TABLE children (
    id                         BIGSERIAL    PRIMARY KEY,
    user_id                    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    full_name                  VARCHAR(150) NOT NULL,
    date_of_birth              DATE,
    custody                    VARCHAR(20)  NOT NULL DEFAULT 'TOGETHER',
    other_parent_name          VARCHAR(150),
    other_parent_date_of_birth DATE
);
CREATE INDEX children_user_idx ON children (user_id);

-- A household-wide custody set in V8 becomes one child with that arrangement.
INSERT INTO children (user_id, full_name, custody, other_parent_name, other_parent_date_of_birth)
SELECT user_id, 'My child', custody, other_parent_name, other_parent_date_of_birth
FROM profiles WHERE custody <> 'TOGETHER' OR other_parent_name IS NOT NULL;

ALTER TABLE profiles DROP COLUMN custody;
ALTER TABLE profiles DROP COLUMN other_parent_name;
ALTER TABLE profiles DROP COLUMN other_parent_date_of_birth;

-- Demo account: Fiona and Marc's daughter.
INSERT INTO children (user_id, full_name, date_of_birth)
SELECT id, 'Léa Gagnon', DATE '2019-05-12' FROM users WHERE username = 'fiona';
