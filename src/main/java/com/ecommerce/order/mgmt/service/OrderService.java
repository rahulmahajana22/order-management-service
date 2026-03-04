package com.ecommerce.order.mgmt.service;

import com.ecommerce.order.mgmt.dto.request.CreateOrderRequest;
import com.ecommerce.order.mgmt.dto.request.UpdateStatusRequest;
import com.ecommerce.order.mgmt.dto.response.OrderResponse;
import com.ecommerce.order.mgmt.entity.Customer;
import com.ecommerce.order.mgmt.entity.Order;
import com.ecommerce.order.mgmt.entity.OrderItem;
import com.ecommerce.order.mgmt.entity.Product;
import com.ecommerce.order.mgmt.enums.OrderStatus;
import com.ecommerce.order.mgmt.exception.BusinessException;
import com.ecommerce.order.mgmt.exception.ResourceNotFoundException;
import com.ecommerce.order.mgmt.repository.CustomerRepository;
import com.ecommerce.order.mgmt.repository.OrderRepository;
import com.ecommerce.order.mgmt.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    // Valid manual status transitions
    private static final List<OrderStatus> MANUAL_PROGRESSION =
            List.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

        Order order = Order.builder()
                .customer(customer)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build();

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (var itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemRequest.productId()));

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemRequest.quantity())
                    .unitPrice(product.getPrice())
                    .build();

            items.add(item);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity())));
        }

        order.setItems(items);
        order.setTotalAmount(total);

        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        return OrderResponse.from(findById(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(OrderStatus status) {
        List<Order> orders = (status != null)
                ? orderRepository.findByStatus(status)
                : orderRepository.findAll();

        return orders.stream().map(OrderResponse::from).toList();
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public OrderResponse updateStatus(Long id, UpdateStatusRequest request) {
        Order order = findById(id);
        OrderStatus newStatus = request.status();

        if (newStatus == OrderStatus.CANCELLED) {
            return cancelOrder(id);
        }

        if (!MANUAL_PROGRESSION.contains(newStatus)) {
            throw new BusinessException("Cannot manually set status to: " + newStatus);
        }

        int currentIndex = MANUAL_PROGRESSION.indexOf(order.getStatus());
        int newIndex = MANUAL_PROGRESSION.indexOf(newStatus);

        // PENDING is before MANUAL_PROGRESSION starts — treat its index as -1
        if (order.getStatus() == OrderStatus.PENDING) {
            currentIndex = -1;
        }

        if (newIndex != currentIndex + 1) {
            throw new BusinessException(
                    "Invalid status transition from " + order.getStatus() + " to " + newStatus);
        }

        order.setStatus(newStatus);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public OrderResponse cancelOrder(Long id) {
        Order order = findById(id);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(
                    "Only PENDING orders can be cancelled. Current status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.from(orderRepository.save(order));
    }

    private Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }
}
