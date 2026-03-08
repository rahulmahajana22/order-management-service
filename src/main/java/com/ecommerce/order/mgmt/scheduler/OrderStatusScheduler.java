package com.ecommerce.order.mgmt.scheduler;

import com.ecommerce.order.mgmt.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusScheduler {

    private final OrderService orderService;

    @Scheduled(fixedRateString = "${app.scheduler.order-processing-rate}")
    public void processPendingOrders() {
        log.debug("Scheduler: checking for PENDING orders...");
        orderService.processPendingOrders();
    }
}
