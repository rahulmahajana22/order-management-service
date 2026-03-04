-- Note: seed users are created by DataInitializer at startup (BCrypt-encoded passwords)

INSERT INTO customers (name, email, phone) VALUES
    ('John Doe',    'john@example.com',  '555-0101'),
    ('Jane Smith',  'jane@example.com',  '555-0102'),
    ('Bob Johnson', 'bob@example.com',   '555-0103');

INSERT INTO products (name, description, price) VALUES
    ('Laptop Stand',        'Adjustable aluminum laptop stand',   49.99),
    ('USB Hub',             '7-port USB 3.0 hub',                 29.99),
    ('Wireless Mouse',      'Ergonomic wireless mouse',           39.99),
    ('Mechanical Keyboard', 'Compact tenkeyless mechanical keyboard', 89.99),
    ('Monitor Light',       'LED monitor light bar',              35.99);
