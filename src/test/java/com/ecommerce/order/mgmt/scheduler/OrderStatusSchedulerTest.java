package com.ecommerce.order.mgmt.scheduler;

import com.ecommerce.order.mgmt.entity.Customer;
import com.ecommerce.order.mgmt.entity.Order;
import com.ecommerce.order.mgmt.enums.OrderStatus;
import com.ecommerce.order.mgmt.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStatusSchedulerTest {

    @Mock  private OrderRepository orderRepository;
    @InjectMocks private OrderStatusScheduler scheduler;

    private Order pendingOrder1;
    private Order pendingOrder2;

    @BeforeEach
    void setUp() {
        Customer customer = Customer.builder()
                .id(1L).name("John Doe").email("john@example.com").build();

        pendingOrder1 = Order.builder()
                .id(1L).customer(customer).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).createdAt(LocalDateTime.now())
                .items(new ArrayList<>()).build();

        pendingOrder2 = Order.builder()
                .id(2L).customer(customer).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).createdAt(LocalDateTime.now())
                .items(new ArrayList<>()).build();
    }

    @Test
    void processPendingOrders_withPendingOrders_advancesToProcessing() {
        when(orderRepository.findByStatus(OrderStatus.PENDING))
                .thenReturn(List.of(pendingOrder1, pendingOrder2));

        scheduler.processPendingOrders();

        assertThat(pendingOrder1.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(pendingOrder2.getStatus()).isEqualTo(OrderStatus.PROCESSING);

        verify(orderRepository).saveAll(argThat(saved ->
                saved instanceof List<?> list && list.size() == 2
                        && list.contains(pendingOrder1) && list.contains(pendingOrder2)));
    }

    @Test
    void processPendingOrders_noPendingOrders_savesNothing() {
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of());

        scheduler.processPendingOrders();

        verify(orderRepository, never()).saveAll(anyIterable());
    }

    @Test
    void processPendingOrders_onlyAdvancesPendingOrders() {
        when(orderRepository.findByStatus(OrderStatus.PENDING))
                .thenReturn(List.of(pendingOrder1));

        scheduler.processPendingOrders();

        assertThat(pendingOrder1.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(pendingOrder2.getStatus()).isEqualTo(OrderStatus.PENDING);

        verify(orderRepository).saveAll(argThat(saved ->
                saved instanceof List<?> list && list.size() == 1 && list.contains(pendingOrder1)));
    }
}
