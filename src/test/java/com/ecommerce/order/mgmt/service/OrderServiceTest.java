package com.ecommerce.order.mgmt.service;

import com.ecommerce.order.mgmt.dto.request.CreateOrderRequest;
import com.ecommerce.order.mgmt.dto.request.OrderItemRequest;
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
import com.ecommerce.order.mgmt.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private SecurityService securityService;

    @InjectMocks
    private OrderService orderService;

    private Customer customer;
    private Product product;
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        customer = Customer.builder()
                .id(1L).name("John Doe").email("john@example.com").phone("555-0101")
                .build();

        product = Product.builder()
                .id(1L).name("Laptop Stand").description("Adjustable stand").price(new BigDecimal("49.99"))
                .build();

        pendingOrder = Order.builder()
                .id(1L).customer(customer).status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("49.99")).createdAt(LocalDateTime.now())
                .items(new ArrayList<>(List.of(
                        OrderItem.builder().id(1L).product(product).quantity(1)
                                .unitPrice(new BigDecimal("49.99")).build()
                )))
                .build();

        // Default: behave as ADMIN. Lenient because tests that don't reach the security
        // check (e.g. cancelOrder, updateStatus) would otherwise trigger UnnecessaryStubbingException.
        lenient().when(securityService.isCurrentUserAdmin()).thenReturn(true);
    }

    // ─── createOrder ────────────────────────────────────────────────────────────

    @Test
    void createOrder_success() {
        CreateOrderRequest request = new CreateOrderRequest(1L,
                List.of(new OrderItemRequest(1L, 2)));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            o.setCreatedAt(LocalDateTime.now());
            return o;
        });

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo("99.98");
        assertThat(response.items()).hasSize(1);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_customerNotFound_throwsResourceNotFoundException() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderService.createOrder(new CreateOrderRequest(99L, List.of(new OrderItemRequest(1L, 1))))
        ).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer");
    }

    @Test
    void createOrder_productNotFound_throwsResourceNotFoundException() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderService.createOrder(new CreateOrderRequest(1L, List.of(new OrderItemRequest(99L, 1))))
        ).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product");
    }

    @Test
    void createOrder_userRoleWrongCustomer_throwsBusinessException() {
        when(securityService.isCurrentUserAdmin()).thenReturn(false);
        when(securityService.getCurrentUserEmail()).thenReturn(Optional.of("other@example.com"));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() ->
                orderService.createOrder(new CreateOrderRequest(1L, List.of(new OrderItemRequest(1L, 1))))
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("own account");
    }

    // ─── getOrder ───────────────────────────────────────────────────────────────

    @Test
    void getOrder_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

        OrderResponse response = orderService.getOrder(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.customerName()).isEqualTo("John Doe");
    }

    @Test
    void getOrder_notFound_throwsResourceNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order");
    }

    @Test
    void getOrder_userRoleOtherOrder_throwsResourceNotFoundException() {
        when(securityService.isCurrentUserAdmin()).thenReturn(false);
        when(securityService.getCurrentUserEmail()).thenReturn(Optional.of("other@example.com"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

        // Returns 404 instead of 403 to avoid leaking order existence
        assertThatThrownBy(() -> orderService.getOrder(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── listOrders ─────────────────────────────────────────────────────────────

    @Test
    void listOrders_adminNoFilter_returnsAll() {
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(pendingOrder)));

        Page<OrderResponse> page = orderService.listOrders(null, Pageable.unpaged());

        assertThat(page.getContent()).hasSize(1);
        verify(orderRepository).findAll(any(Pageable.class));
        verify(orderRepository, never()).findByStatus(any(), any(Pageable.class));
    }

    @Test
    void listOrders_adminWithStatusFilter_returnsFiltered() {
        when(orderRepository.findByStatus(eq(OrderStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingOrder)));

        Page<OrderResponse> page = orderService.listOrders(OrderStatus.PENDING, Pageable.unpaged());

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).status()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository).findByStatus(eq(OrderStatus.PENDING), any(Pageable.class));
        verify(orderRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listOrders_userRole_returnsOwnOrdersOnly() {
        when(securityService.isCurrentUserAdmin()).thenReturn(false);
        when(securityService.getCurrentUserEmail()).thenReturn(Optional.of("john@example.com"));
        when(orderRepository.findByCustomerEmail(eq("john@example.com"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingOrder)));

        Page<OrderResponse> page = orderService.listOrders(null, Pageable.unpaged());

        assertThat(page.getContent()).hasSize(1);
        verify(orderRepository).findByCustomerEmail(eq("john@example.com"), any(Pageable.class));
        verify(orderRepository, never()).findAll(any(Pageable.class));
    }

    // ─── updateStatus ───────────────────────────────────────────────────────────

    @Test
    void updateStatus_pendingToProcessing_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus(1L, new UpdateStatusRequest(OrderStatus.PROCESSING));

        assertThat(response.status()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void updateStatus_processingToShipped_success() {
        pendingOrder.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus(1L, new UpdateStatusRequest(OrderStatus.SHIPPED));

        assertThat(response.status()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void updateStatus_shippedToDelivered_success() {
        pendingOrder.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus(1L, new UpdateStatusRequest(OrderStatus.DELIVERED));

        assertThat(response.status()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void updateStatus_invalidTransition_throwsBusinessException() {
        // PENDING cannot jump to SHIPPED (skipping PROCESSING)
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() ->
                orderService.updateStatus(1L, new UpdateStatusRequest(OrderStatus.SHIPPED))
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    void updateStatus_toCancelled_delegatesToCancelOrder() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus(1L, new UpdateStatusRequest(OrderStatus.CANCELLED));

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void updateStatus_cannotSetToPending_throwsBusinessException() {
        pendingOrder.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() ->
                orderService.updateStatus(1L, new UpdateStatusRequest(OrderStatus.PENDING))
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot manually set status to");
    }

    // ─── cancelOrder ────────────────────────────────────────────────────────────

    @Test
    void cancelOrder_pendingOrder_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(1L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(pendingOrder);
    }

    @Test
    void cancelOrder_processingOrder_throwsBusinessException() {
        pendingOrder.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only PENDING orders can be cancelled");
    }

    @Test
    void cancelOrder_deliveredOrder_throwsBusinessException() {
        pendingOrder.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only PENDING orders can be cancelled");
    }
}
