-- Demo accounts, both with the password "demo1234". All names and details are fictional.
-- Remove these rows before any real use.

INSERT INTO users (username, display_name, password_hash) VALUES
    ('fiona', 'Fiona Tremblay', '$2a$10$5NugZZ85xJIwGbm/Vh8cFOgsVzera.PdBbygHeGlyyLK18/zX.d0C'),
    ('sam',   'Sam Okafor',     '$2a$10$5NugZZ85xJIwGbm/Vh8cFOgsVzera.PdBbygHeGlyyLK18/zX.d0C');

INSERT INTO profiles (user_id, full_name, date_of_birth, street, city, province, postal_code, phone)
SELECT id, 'Fiona Tremblay', DATE '1991-04-17', '4820 rue Fabre', 'Montreal', 'QC', 'H2J 3W1', '514-555-0142'
FROM users WHERE username = 'fiona';
