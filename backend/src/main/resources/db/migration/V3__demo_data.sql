-- Demo departments and accounts for the fictional company Northbridge Software.
-- Every demo account uses the password "demo1234". Change or remove them before any real use.

INSERT INTO departments (code, name) VALUES
    ('HR', 'Human Resources'),
    ('IT', 'IT'),
    ('FINANCE', 'Finance');

INSERT INTO users (username, display_name, password_hash, role, department_id) VALUES
    ('admin', 'Avery Admin',  '$2a$10$5NugZZ85xJIwGbm/Vh8cFOgsVzera.PdBbygHeGlyyLK18/zX.d0C', 'ADMIN',
        (SELECT id FROM departments WHERE code = 'IT')),
    ('hana',  'Hana Park',    '$2a$10$5NugZZ85xJIwGbm/Vh8cFOgsVzera.PdBbygHeGlyyLK18/zX.d0C', 'KNOWLEDGE_MANAGER',
        (SELECT id FROM departments WHERE code = 'HR')),
    ('ivan',  'Ivan Morel',   '$2a$10$5NugZZ85xJIwGbm/Vh8cFOgsVzera.PdBbygHeGlyyLK18/zX.d0C', 'EMPLOYEE',
        (SELECT id FROM departments WHERE code = 'IT')),
    ('fiona', 'Fiona Tremblay', '$2a$10$5NugZZ85xJIwGbm/Vh8cFOgsVzera.PdBbygHeGlyyLK18/zX.d0C', 'EMPLOYEE',
        (SELECT id FROM departments WHERE code = 'FINANCE'));
