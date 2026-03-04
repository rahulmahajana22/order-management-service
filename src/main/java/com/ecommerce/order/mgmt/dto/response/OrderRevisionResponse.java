package com.ecommerce.order.mgmt.dto.response;

import java.time.Instant;

public record OrderRevisionResponse(
        int revision,
        String revisionType,
        Instant timestamp,
        OrderResponse order) {}
