# Create an order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "items": [
      { "productId": 1, "quantity": 2 },
      { "productId": 2, "quantity": 1 }
    ]
  }'

# Get order by ID
curl http://localhost:8080/api/orders/1

# List all orders
curl http://localhost:8080/api/orders

# List orders filtered by status
curl "http://localhost:8080/api/orders?status=PENDING"
curl "http://localhost:8080/api/orders?status=PROCESSING"
curl "http://localhost:8080/api/orders?status=SHIPPED"
curl "http://localhost:8080/api/orders?status=DELIVERED"
curl "http://localhost:8080/api/orders?status=CANCELLED"

# Update order status  (PENDING → PROCESSING → SHIPPED → DELIVERED)
curl -X PATCH http://localhost:8080/api/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{ "status": "PROCESSING" }'

curl -X PATCH http://localhost:8080/api/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{ "status": "SHIPPED" }'

curl -X PATCH http://localhost:8080/api/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{ "status": "DELIVERED" }'

# Cancel an order (only works if PENDING)
curl -X DELETE http://localhost:8080/api/orders/1

# H2 console (browser)
# http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:orderdb  |  User: sa  |  Password: (empty)
