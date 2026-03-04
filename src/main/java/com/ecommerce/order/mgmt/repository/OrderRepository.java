package com.ecommerce.order.mgmt.repository;

import com.ecommerce.order.mgmt.entity.Order;
import com.ecommerce.order.mgmt.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatus(OrderStatus status);
}
