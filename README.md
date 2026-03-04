# Order Management Service

A lightweight backend for processing e-commerce orders. Built with Spring Boot 3, Java 25, and an H2 in-memory database, it exposes a simple REST API for placing orders, tracking status, and manual operations. A scheduled job automatically advances pending orders to processing.

---

## 🧭 Overview

This application implements the core of an order management system without authentication. Customers can place orders containing multiple products. Orders follow a lifecycle and may be advanced automatically via a scheduler or manually via API calls. The database is initialized on startup with sample customers and products.

## ✅ Features

- Place new orders with multiple items
- Retrieve single orders or list all orders (optional status filter)
- Manual status progression (`PROCESSING`, `SHIPPED`, `DELIVERED`)
- Cancel pending orders
- Background scheduler moves **PENDING** orders to **PROCESSING** every 5 minutes
- Validation, exception handling, and in-memory H2 database with console
- Comprehensive unit tests for controller, service and scheduler logic

## 🛠 Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 25 |
| Framework | Spring Boot 3.5.12-SNAPSHOT |
| Persistence | Spring Data JPA, H2 in-memory DB |
| Build | Maven (wrapper included) |
| Validation | Jakarta Bean Validation |
| Logging | Lombok + SLF4J |
| Testing | JUnit 5, Mockito, Spring MVC Test |

---

## 🚀 Getting Started

### Prerequisites

- JDK 25+
- Maven (wrapped script included: `./mvnw` or `mvnw.cmd` on Windows)

### Build & Run

```powershell
# Windows
mvnw clean package
mvnw spring-boot:run
```

or use your IDE to run `com.ecommerce.order.mgmt.OrderManagementApplication`.

The application listens on port **8080** by default.

### Configuration

`src/main/resources/application.properties` contains all important settings:

```properties
spring.application.name=order-management

# H2 database
spring.datasource.url=jdbc:h2:mem:orderdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=${H2_DB_USERNAME}
spring.datasource.password=${H2_DB_PASSWORD}
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# JPA
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Scheduler (milliseconds)
app.scheduler.order-processing-rate=300000

# SQL initialization
spring.sql.init.mode=always
spring.jpa.defer-datasource-initialization=true
```

You can adjust `app.scheduler.order-processing-rate` to change how often pending orders are processed.

---

## 📦 Database Schema & Sample Data

The schema is defined in `schema.sql` and sample data in `data.sql`. The initialization runs on startup thanks to `spring.sql.init.mode=always`.

### Key Tables

- `customers` – id, name, email, phone
- `products` – id, name, description, price
- `orders` – id, customer_id, status, total_amount, created_at
- `order_items` – id, order_id, product_id, quantity, unit_price

### Sample Data

Three customers and five products are inserted automatically for testing purposes.

Access the H2 console at [http://localhost:8080/h2-console](http://localhost:8080/h2-console).
JDBC URL: `jdbc:h2:mem:orderdb` (user `sa`, no password).

---

## 🗂 Domain Model

Entities reside under `com.ecommerce.order.mgmt.entity`:

- **Customer** – basic contact info
- **Product** – name, description, price
- **Order** – links to customer, status, total, creation timestamp, and items
- **OrderItem** – links to order and product, quantity, unit price

Relationships:

```
Customer (1) ──── (N) Order
Order (1) ──── (N) OrderItem
OrderItem (N) ──── (1) Product
```

---

## 🔁 Order Lifecycle & Business Rules

Statuses defined in `com.ecommerce.order.mgmt.enums.OrderStatus`:

```
PENDING → PROCESSING → SHIPPED → DELIVERED
       ↘ CANCELLED (only from PENDING)
```

- **Pending orders** are automatically advanced to **Processing** by a scheduled task (configurable rate).
- **Manual transitions** allowed via API: PENDING → PROCESSING → SHIPPED → DELIVERED.
- **Cancel** allowed only when status is `PENDING`; changing status to `CANCELLED` via PATCH or DELETE endpoint.
- Invalid transitions or attempts to cancel non-pending orders result in `BusinessException`.

All transactional operations use appropriate isolation (`REPEATABLE_READ` for service, `SERIALIZABLE` for scheduler) to avoid race conditions.

---

## 📡 REST API

Base path: `/api/orders`

| Method | Endpoint                 | Description                                   | Input / Query                    | Status Codes |
|--------|--------------------------|-----------------------------------------------|----------------------------------|--------------|
| POST   | `/api/orders`            | Place a new order                             | `CreateOrderRequest` (JSON)      | 201, 400, 404|
| GET    | `/api/orders/{id}`       | Get order details                             | None                             | 200, 404     |
| GET    | `/api/orders`            | List orders, optional filter by status        | `?status=PENDING` etc.           | 200          |
| PATCH  | `/api/orders/{id}/status`| Manually update status                        | `UpdateStatusRequest` (JSON)     | 200, 400,404 |
| DELETE | `/api/orders/{id}`       | Cancel an order (must be PENDING)             | None                             | 200, 400,404 |

### Request/Response DTOs

**CreateOrderRequest**
```json
{
  "customerId": 1,
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

**OrderResponse**
```json
{
  "id": 1,
  "customerName": "John Doe",
  "customerEmail": "john@example.com",
  "status": "PENDING",
  "totalAmount": 149.97,
  "createdAt": "2026-03-04T11:00:00",
  "items": [
    { "id": 10, "productName": "Laptop Stand", "quantity": 2, "unitPrice": 49.99, "subtotal": 99.98 },
    { "id": 11, "productName": "USB Hub", "quantity": 1, "unitPrice": 49.99, "subtotal": 49.99 }
  ]
}
```

Validation annotations ensure required fields and positive quantities. Invalid input returns HTTP 400 with field-specific errors.

### Example curl commands

```bash
# place order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":1,"items":[{"productId":1,"quantity":2}]}'

# get order
echo GET http://localhost:8080/api/orders/1

# list all orders
curl http://localhost:8080/api/orders

# filter by status
curl "http://localhost:8080/api/orders?status=PROCESSING"

# update status
curl -X PATCH http://localhost:8080/api/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{"status":"SHIPPED"}'

# cancel order
curl -X DELETE http://localhost:8080/api/orders/1
```

---

## ⚠️ Error Handling

`GlobalExceptionHandler` maps exceptions to meaningful HTTP responses:

| Exception                           | HTTP | Description |
|-------------------------------------|------|-------------|
| `ResourceNotFoundException`         | 404  | Entity missing (order, product, customer) |
| `BusinessException`                 | 400  | Rule violation (invalid status, cancellation) |
| `MethodArgumentNotValidException`   | 400  | Validation errors with field map |
| Any other `Exception`              | 500  | Generic unexpected error |

Response bodies include timestamp, status, message, and path.

---

## 🧪 Testing

Unit tests are located under `src/test/java`:

- **OrderServiceTest** – service logic and business rules
- **OrderControllerTest** – API layer with MockMvc
- **OrderStatusSchedulerTest** – scheduled job behavior

Run all tests with Maven:

```powershell
mvnw test
```

A successful build produces generated reports in `target/surefire-reports`.

---

## 🔄 Scheduler

`OrderStatusScheduler` runs every `app.scheduler.order-processing-rate` milliseconds (default 300000 = 5 min).
It queries pending orders and marks them as processing in a serializable transaction to prevent concurrent modifications.

You can disable scheduling by setting `spring.task.scheduling.enabled=false`.

---

## 🧩 Extensibility & Notes

- **Persistence**: Swap H2 for a production database by adjusting `spring.datasource` settings and removing `schema.sql`/`data.sql` initialization.
- **Security**: Add Spring Security filters and authentication as needed.
- **Pagination / Sorting**: Extend the list endpoint via Spring Data `Pageable`.
- **Performance**: Add indexes or adjust fetch strategies; current lazy associations keep entity graphs manageable.

---

## 📄 License

This project is provided as-is for educational purposes.

The source code in this repository is not explicitly licensed; you may wish to
choose an appropriate open‑source license (e.g. Apache 2.0) before redistributing.

The dependencies declared in `pom.xml` and their usual licenses are:

* **Spring Boot Starter Parent** – Apache License 2.0 (inherits for all Spring components)
* **spring-boot-starter-actuator** – Apache 2.0
* **spring-boot-starter-web** – Apache 2.0
* **spring-boot-starter-data-jpa** – Apache 2.0
* **spring-boot-starter-validation** – Apache 2.0
* **spring-boot-starter-test** (test scope) – Apache 2.0
* **h2** – MPL 2.0 (file-based / in-memory database)
* **spring-boot-devtools** – Apache 2.0 (runtime, optional)
* **lombok** – MIT License (provided/optional)

All Spring projects are Apache‑2.0; verify versions in the corresponding POMs. The H2 database uses the Mozilla Public License 2.0. Lombok is distributed under the MIT License. Should you add further libraries, consult their POMs or project sites for licensing details.

Refer to each dependency's POM or website for full details.

---

> **Tip:** use `mvnw spring-boot:run` in one terminal and hit endpoints with the examples above to experiment. The H2 console makes it easy to inspect tables and data.

---

*Generated README summarizing full project details.*
