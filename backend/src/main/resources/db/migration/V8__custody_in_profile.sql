-- Separated or divorced parents: the children's custody and their other parent decide which plan
-- pays first for a child.
ALTER TABLE profiles ADD COLUMN custody VARCHAR(20) NOT NULL DEFAULT 'TOGETHER';
ALTER TABLE profiles ADD COLUMN other_parent_name VARCHAR(150);
ALTER TABLE profiles ADD COLUMN other_parent_date_of_birth DATE;
