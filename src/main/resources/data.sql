-- ─────────────────────────────────────────────────────────────────────────────
-- Seed data — customers and products only.
-- Orders are created via the API to go through proper business logic.
-- Seed users (admin / user) are created by DataInitializer at startup.
-- All INSERTs are idempotent: they skip if the row already exists.
-- ─────────────────────────────────────────────────────────────────────────────

-- ─── Customers ───────────────────────────────────────────────────────────────
INSERT INTO customers (name, email, phone, created_at, updated_at)
    SELECT 'Alice Johnson', 'alice@example.com', '555-0101',
           '2025-02-01 10:00:00', '2025-02-01 10:00:00'
    WHERE NOT EXISTS (SELECT 1 FROM customers WHERE email = 'alice@example.com');

INSERT INTO customers (name, email, phone, created_at, updated_at)
    SELECT 'Bob Smith', 'bob@example.com', '555-0202',
           '2025-02-15 14:30:00', '2025-02-15 14:30:00'
    WHERE NOT EXISTS (SELECT 1 FROM customers WHERE email = 'bob@example.com');

INSERT INTO customers (name, email, phone, created_at, updated_at)
    SELECT 'Carol White', 'carol@example.com', '555-0303',
           '2025-03-01 09:15:00', '2025-03-01 09:15:00'
    WHERE NOT EXISTS (SELECT 1 FROM customers WHERE email = 'carol@example.com');

-- ─── Products ────────────────────────────────────────────────────────────────
INSERT INTO products (name, description, price, created_at, updated_at)
    SELECT 'Laptop Stand', 'Adjustable aluminum laptop stand', 49.99,
           '2025-01-01 08:00:00', '2025-01-01 08:00:00'
    WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Laptop Stand');

INSERT INTO products (name, description, price, created_at, updated_at)
    SELECT 'USB Hub', '7-port USB 3.0 hub', 29.99,
           '2025-01-01 08:00:00', '2025-01-01 08:00:00'
    WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'USB Hub');

INSERT INTO products (name, description, price, created_at, updated_at)
    SELECT 'Wireless Mouse', 'Ergonomic wireless mouse', 39.99,
           '2025-01-01 08:00:00', '2025-01-01 08:00:00'
    WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Wireless Mouse');

INSERT INTO products (name, description, price, created_at, updated_at)
    SELECT 'Mechanical Keyboard', 'Compact tenkeyless mechanical keyboard', 89.99,
           '2025-01-05 08:00:00', '2025-01-05 08:00:00'
    WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Mechanical Keyboard');

INSERT INTO products (name, description, price, created_at, updated_at)
    SELECT 'Monitor Light', 'LED monitor light bar', 35.99,
           '2025-01-05 08:00:00', '2025-01-05 08:00:00'
    WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Monitor Light');
