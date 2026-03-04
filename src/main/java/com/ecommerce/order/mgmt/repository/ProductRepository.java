package com.ecommerce.order.mgmt.repository;

import com.ecommerce.order.mgmt.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
