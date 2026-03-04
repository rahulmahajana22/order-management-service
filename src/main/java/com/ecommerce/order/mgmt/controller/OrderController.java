package com.ecommerce.order.mgmt.controller;

import com.ecommerce.order.mgmt.dto.request.CreateOrderRequest;
import com.ecommerce.order.mgmt.dto.request.UpdateStatusRequest;
import com.ecommerce.order.mgmt.dto.response.OrderResponse;
import com.ecommerce.order.mgmt.dto.response.OrderRevisionResponse;
import com.ecommerce.order.mgmt.enums.OrderStatus;
import com.ecommerce.order.mgmt.service.OrderService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /api/orders
     * Place a new order with multiple items.
     */
    @PostMapping
    @RateLimiter(name = "orders")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    /**
     * GET /api/orders/{id}
     * Retrieve order details by ID.
     */
    @GetMapping("/{id}")
    @RateLimiter(name = "orders")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    /**
     * GET /api/orders
     * List orders with optional status filter and pagination.
     * ADMIN sees all orders; USER sees only their own.
     * Example: GET /api/orders?status=PENDING&page=0&size=10&sort=createdAt,desc
     */
    @GetMapping
    @RateLimiter(name = "orders")
    public ResponseEntity<Page<OrderResponse>> listOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(orderService.listOrders(status, pageable));
    }

    /**
     * PATCH /api/orders/{id}/status
     * Manually update order status (PROCESSING → SHIPPED → DELIVERED). ADMIN only.
     */
    @PatchMapping("/{id}/status")
    @RateLimiter(name = "orders")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(orderService.updateStatus(id, request));
    }

    /**
     * DELETE /api/orders/{id}
     * Cancel an order — only allowed when status is PENDING. ADMIN only.
     */
    @DeleteMapping("/{id}")
    @RateLimiter(name = "orders")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    /**
     * GET /api/orders/{id}/revisions
     * Retrieve the full audit revision history for an order.
     * ADMIN sees any order; USER sees only their own.
     */
    @GetMapping("/{id}/revisions")
    @RateLimiter(name = "orders")
    public ResponseEntity<List<OrderRevisionResponse>> getOrderRevisions(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderRevisions(id));
    }
}
