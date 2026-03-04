package com.ecommerce.order.mgmt.dto.response;

import com.ecommerce.order.mgmt.entity.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
        );
    }
}
