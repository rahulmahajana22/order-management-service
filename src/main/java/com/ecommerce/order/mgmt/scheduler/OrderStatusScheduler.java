package com.ecommerce.order.mgmt.scheduler;

import com.ecommerce.order.mgmt.entity.Order;
import com.ecommerce.order.mgmt.enums.OrderStatus;
import com.ecommerce.order.mgmt.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusScheduler {

    private final OrderRepository orderRepository;

    @Scheduled(fixedRateString = "${app.scheduler.order-processing-rate}")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processPendingOrders() {
        List<Order> pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING);

        if (pendingOrders.isEmpty()) {
            log.debug("Scheduler: no PENDING orders to process.");
            return;
        }

        pendingOrders.forEach(order -> order.setStatus(OrderStatus.PROCESSING));
        orderRepository.saveAll(pendingOrders);

        log.info("Scheduler: advanced {} PENDING order(s) to PROCESSING.", pendingOrders.size());
    }
}
