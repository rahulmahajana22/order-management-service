package com.ecommerce.order.mgmt.dto.response;

public record AuthResponse(
        String token,
        String username,
        String role
) {}
