-- ─── Drop in dependency order ────────────────────────────────────────────────
DROP TABLE IF EXISTS orders_aud;
DROP TABLE IF EXISTS REVINFO;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS users;

-- ─── Auth ─────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    role       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

-- ─── Business domain ──────────────────────────────────────────────────────────
CREATE TABLE customers (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    phone      VARCHAR(50),
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE TABLE products (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255)   NOT NULL,
    description VARCHAR(1000),
    price       DECIMAL(10, 2) NOT NULL,
    created_at  TIMESTAMP      NOT NULL,
    updated_at  TIMESTAMP      NOT NULL
);

CREATE TABLE orders (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id      BIGINT         NOT NULL,
    status           VARCHAR(50)    NOT NULL,
    total_amount     DECIMAL(10, 2) NOT NULL,
    created_at       TIMESTAMP      NOT NULL,
    created_by       VARCHAR(50),
    last_modified_by VARCHAR(50),
    updated_at       TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CREATE TABLE order_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT         NOT NULL,
    product_id BIGINT         NOT NULL,
    quantity   INT            NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP      NOT NULL,
    FOREIGN KEY (order_id)   REFERENCES orders (id),
    FOREIGN KEY (product_id) REFERENCES products (id)
);

-- ─── Envers audit tables ──────────────────────────────────────────────────────
-- One row per transaction that modified an @Audited entity
CREATE TABLE REVINFO (
    REV      INT AUTO_INCREMENT PRIMARY KEY,
    REVTSTMP BIGINT NOT NULL
);

-- Full change history for the orders table
CREATE TABLE orders_aud (
    id               BIGINT         NOT NULL,
    REV              INT            NOT NULL,
    REVTYPE          TINYINT,
    customer_id      BIGINT,
    status           VARCHAR(50),
    total_amount     DECIMAL(10, 2),
    created_at       TIMESTAMP,
    created_by       VARCHAR(50),
    last_modified_by VARCHAR(50),
    updated_at       TIMESTAMP,
    PRIMARY KEY (id, REV),
    FOREIGN KEY (REV) REFERENCES REVINFO (REV)
);
