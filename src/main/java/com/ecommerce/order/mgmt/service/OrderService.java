package com.ecommerce.order.mgmt.service;

import com.ecommerce.order.mgmt.dto.request.CreateOrderRequest;
import com.ecommerce.order.mgmt.dto.request.UpdateStatusRequest;
import com.ecommerce.order.mgmt.dto.response.OrderResponse;
import com.ecommerce.order.mgmt.dto.response.OrderRevisionResponse;
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
import com.ecommerce.order.mgmt.security.SecurityService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.DefaultRevisionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final SecurityService securityService;

    @PersistenceContext
    private EntityManager entityManager;

    // Valid manual status transitions
    private static final List<OrderStatus> MANUAL_PROGRESSION =
            List.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

        // USER role: can only place orders for their own customer account (matched by email)
        if (!securityService.isCurrentUserAdmin()) {
            String userEmail = securityService.getCurrentUserEmail()
                    .orElseThrow(() -> new BusinessException("Cannot determine authenticated user email"));
            if (!customer.getEmail().equals(userEmail)) {
                throw new BusinessException("You can only place orders for your own account");
            }
        }

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
        Order order = findById(id);

        // USER role: can only view their own orders
        if (!securityService.isCurrentUserAdmin()) {
            String userEmail = securityService.getCurrentUserEmail()
                    .orElseThrow(() -> new BusinessException("Cannot determine authenticated user email"));
            if (!order.getCustomer().getEmail().equals(userEmail)) {
                throw new ResourceNotFoundException("Order", id);
            }
        }

        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(OrderStatus status, Pageable pageable) {
        if (securityService.isCurrentUserAdmin()) {
            Page<Order> orders = (status != null)
                    ? orderRepository.findByStatus(status, pageable)
                    : orderRepository.findAll(pageable);
            return orders.map(OrderResponse::from);
        }

        // USER: only their own orders, matched via customer email
        String userEmail = securityService.getCurrentUserEmail()
                .orElseThrow(() -> new BusinessException("Cannot determine authenticated user email"));
        Page<Order> orders = (status != null)
                ? orderRepository.findByStatusAndCustomerEmail(status, userEmail, pageable)
                : orderRepository.findByCustomerEmail(userEmail, pageable);
        return orders.map(OrderResponse::from);
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<OrderRevisionResponse> getOrderRevisions(Long id) {
        Order order = findById(id);

        // USER role: can only view revisions for their own orders
        if (!securityService.isCurrentUserAdmin()) {
            String userEmail = securityService.getCurrentUserEmail()
                    .orElseThrow(() -> new BusinessException("Cannot determine authenticated user email"));
            if (!order.getCustomer().getEmail().equals(userEmail)) {
                throw new ResourceNotFoundException("Order", id);
            }
        }

        AuditReader reader = AuditReaderFactory.get(entityManager);
        List<Object[]> rows = reader.createQuery()
                .forRevisionsOfEntity(Order.class, false, true)
                .add(AuditEntity.id().eq(id))
                .getResultList();

        return rows.stream()
                .map(row -> {
                    Order entity = (Order) row[0];
                    DefaultRevisionEntity rev = (DefaultRevisionEntity) row[1];
                    RevisionType type = (RevisionType) row[2];
                    return new OrderRevisionResponse(
                            rev.getId(),
                            type.name(),
                            Instant.ofEpochMilli(rev.getTimestamp()),
                            entity != null ? OrderResponse.from(entity) : null
                    );
                })
                .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse cancelOrder(Long id) {
        Order order = findById(id);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(
                    "Only PENDING orders can be cancelled. Current status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processPendingOrders() {
        List<Order> pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING);
        if (pendingOrders.isEmpty()) return;
        pendingOrders.forEach(order -> order.setStatus(OrderStatus.PROCESSING));
        orderRepository.saveAll(pendingOrders);
        log.info("Advanced {} PENDING order(s) to PROCESSING.", pendingOrders.size());
    }

    private Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }
}
