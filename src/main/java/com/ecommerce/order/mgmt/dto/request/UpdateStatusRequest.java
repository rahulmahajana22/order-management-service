package com.ecommerce.order.mgmt.dto.request;

import com.ecommerce.order.mgmt.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(

        @NotNull(message = "Status is required")
        OrderStatus status
) {}
