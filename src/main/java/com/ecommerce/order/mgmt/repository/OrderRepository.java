package com.ecommerce.order.mgmt.repository;

import com.ecommerce.order.mgmt.entity.Order;
import com.ecommerce.order.mgmt.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // ── Used by scheduler (processes all matching orders at once) ─────────────
    List<Order> findByStatus(OrderStatus status);

    // ── Paginated queries for ADMIN (all orders) ──────────────────────────────
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    // ── Paginated queries for USER (own orders, matched via customer email) ───
    Page<Order> findByCustomerEmail(String email, Pageable pageable);

    Page<Order> findByStatusAndCustomerEmail(OrderStatus status, String email, Pageable pageable);
}
