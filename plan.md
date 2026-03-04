# E-commerce Order Processing System — Implementation Plan

## Context
Build a backend REST API for an E-commerce Order Processing System.
Customers can place orders, track status, and perform basic order operations.
A background job automatically advances PENDING orders to PROCESSING every 5 minutes.
No authentication required.

---

## Tech Stack
- Java 25 / Spring Boot 3.5.12-SNAPSHOT
- H2 in-memory database
- Spring Data JPA
- Lombok + Bean Validation
- Custom REST controllers

---

## Step 1 — Update pom.xml Dependencies

Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

Remove `spring-boot-starter-data-rest` (not needed, using custom controllers).

---

## Step 2 — application.properties

```properties
spring.application.name=order-management

# H2
spring.datasource.url=jdbc:h2:mem:orderdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# JPA
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Scheduler
spring.task.scheduling.enabled=true
```

---

## Step 3 — Package Structure

```
com.ecommerce.order.mgmt
├── entity/
│   ├── Customer.java        # id, name, email, phone
│   ├── Product.java         # id, name, description, price
│   ├── Order.java           # id, customer, status, totalAmount, createdAt
│   └── OrderItem.java       # id, order, product, quantity, unitPrice
├── enums/
│   └── OrderStatus.java     # PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED
├── repository/
│   ├── CustomerRepository.java
│   ├── ProductRepository.java
│   ├── OrderRepository.java      # findByStatus(OrderStatus)
│   └── OrderItemRepository.java
├── dto/
│   ├── request/
│   │   ├── CreateOrderRequest.java   # customerId, List<OrderItemRequest>
│   │   └── OrderItemRequest.java     # productId, quantity
│   └── response/
│       ├── OrderResponse.java        # id, customerName, status, totalAmount, items[], createdAt
│       └── OrderItemResponse.java    # productName, quantity, unitPrice, subtotal
├── service/
│   └── OrderService.java     # createOrder(), getOrder(), listOrders(), cancelOrder(), updateStatus()
├── scheduler/
│   └── OrderStatusScheduler.java   # @Scheduled — PENDING → PROCESSING every 5 min
├── controller/
│   └── OrderController.java  # all REST endpoints
└── exception/
    ├── GlobalExceptionHandler.java
    ├── ResourceNotFoundException.java
    └── BusinessException.java
```

---

## Step 4 — Entity Relationships

```
Customer (1) ──── (N) Order
Order    (1) ──── (N) OrderItem
OrderItem(N) ──── (1) Product
```

- `Order` → `@ManyToOne` Customer
- `Order` → `@OneToMany` OrderItems (cascade ALL, orphanRemoval = true)
- `OrderItem` → `@ManyToOne` Product

---

## Step 5 — Order Lifecycle

```
PENDING ──(auto every 5 min)──► PROCESSING ──► SHIPPED ──► DELIVERED
   │
   └──► CANCELLED  (only from PENDING, manual)
```

- Background job runs every 5 minutes: finds all PENDING orders → sets them to PROCESSING
- Manual status update allowed: PROCESSING → SHIPPED → DELIVERED
- Cancel only allowed when status is PENDING

---

## Step 6 — REST API Endpoints

| Method | Endpoint                  | Description                                  |
|--------|---------------------------|----------------------------------------------|
| POST   | /api/orders               | Place a new order with items                 |
| GET    | /api/orders/{id}          | Get order details by ID                      |
| GET    | /api/orders               | List all orders (optional ?status= filter)   |
| PATCH  | /api/orders/{id}/status   | Manually update order status                 |
| DELETE | /api/orders/{id}          | Cancel an order (only if PENDING)            |

---

## Step 7 — Request / Response Shapes

**POST /api/orders**
```json
{
  "customerId": 1,
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

**GET /api/orders/{id} response**
```json
{
  "id": 1,
  "customerName": "John Doe",
  "status": "PENDING",
  "totalAmount": 149.97,
  "createdAt": "2026-03-04T11:00:00",
  "items": [
    { "productName": "Laptop Stand", "quantity": 2, "unitPrice": 49.99, "subtotal": 99.98 },
    { "productName": "USB Hub",      "quantity": 1, "unitPrice": 49.99, "subtotal": 49.99 }
  ]
}
```

---

## Step 8 — Background Scheduler

`OrderStatusScheduler.java`:
```java
@Scheduled(fixedRate = 300000) // every 5 minutes
public void processPendingOrders() {
    // fetch all PENDING orders → set to PROCESSING → save
}
```

Requires `@EnableScheduling` on main class or config.

---

## Step 9 — Exception Handling

`GlobalExceptionHandler` handles:
- `ResourceNotFoundException` → 404 (order/product/customer not found)
- `BusinessException` → 400 (e.g. cancel attempted on non-PENDING order)
- `MethodArgumentNotValidException` → 400 with field-level errors
- Generic `Exception` → 500

---

## Step 10 — Implementation Order

1. `pom.xml` — add JPA, Validation, H2 dependencies; remove data-rest
2. `application.properties` — configure H2, JPA, scheduler
3. Enum: `OrderStatus`
4. Entities: `Customer`, `Product`, `Order`, `OrderItem`
5. Repositories
6. DTOs (request + response)
7. Exceptions + `GlobalExceptionHandler`
8. `OrderService`
9. `OrderStatusScheduler`
10. `OrderController`
11. Add `@EnableScheduling` to main application class

---

## Verification

1. Run `./mvnw spring-boot:run` — starts on port 8080
2. Visit `http://localhost:8080/h2-console` — verify tables exist
3. `POST /api/orders` with valid body → 201 Created
4. `GET /api/orders/{id}` → order with PENDING status
5. Wait 5 minutes (or trigger scheduler manually) → status changes to PROCESSING
6. `DELETE /api/orders/{id}` on a PENDING order → 200 OK (cancelled)
7. `DELETE /api/orders/{id}` on a PROCESSING order → 400 Bad Request
8. `GET /api/orders?status=PROCESSING` → filtered list
