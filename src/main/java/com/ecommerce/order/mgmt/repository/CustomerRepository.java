package com.ecommerce.order.mgmt.repository;

import com.ecommerce.order.mgmt.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
