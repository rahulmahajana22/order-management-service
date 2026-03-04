package com.ecommerce.order.mgmt.repository;

import com.ecommerce.order.mgmt.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
