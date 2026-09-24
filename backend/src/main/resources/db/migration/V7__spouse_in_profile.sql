-- Coordination of benefits needs to know whose plan is whose, and for children, which parent's
-- birthday comes first in the year.
ALTER TABLE profiles ADD COLUMN spouse_name VARCHAR(150);
ALTER TABLE profiles ADD COLUMN spouse_date_of_birth DATE;

-- Demo account: Fiona's husband Marc (the member of the fictional Cedarview plan).
UPDATE profiles SET spouse_name = 'Marc Gagnon', spouse_date_of_birth = DATE '1989-11-02'
WHERE user_id = (SELECT id FROM users WHERE username = 'fiona');
