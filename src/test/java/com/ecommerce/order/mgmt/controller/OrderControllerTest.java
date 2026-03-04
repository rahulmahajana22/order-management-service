package com.ecommerce.order.mgmt.controller;

import com.ecommerce.order.mgmt.config.SecurityConfig;
import com.ecommerce.order.mgmt.dto.request.CreateOrderRequest;
import com.ecommerce.order.mgmt.dto.request.OrderItemRequest;
import com.ecommerce.order.mgmt.dto.request.UpdateStatusRequest;
import com.ecommerce.order.mgmt.dto.response.OrderItemResponse;
import com.ecommerce.order.mgmt.dto.response.OrderResponse;
import com.ecommerce.order.mgmt.enums.OrderStatus;
import com.ecommerce.order.mgmt.exception.BusinessException;
import com.ecommerce.order.mgmt.exception.GlobalExceptionHandler;
import com.ecommerce.order.mgmt.exception.ResourceNotFoundException;
import com.ecommerce.order.mgmt.security.CustomUserDetailsService;
import com.ecommerce.order.mgmt.security.JwtService;
import com.ecommerce.order.mgmt.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@WithMockUser(roles = "ADMIN")
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean OrderService orderService;
    @MockitoBean JwtService jwtService;
    @MockitoBean CustomUserDetailsService userDetailsService;

    private OrderResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = new OrderResponse(
                1L, "John Doe", "john@example.com",
                OrderStatus.PENDING, new BigDecimal("49.99"),
                LocalDateTime.now(),
                List.of(new OrderItemResponse(1L, "Laptop Stand", 1,
                        new BigDecimal("49.99"), new BigDecimal("49.99")))
        );
    }

    // ─── POST /api/orders ───────────────────────────────────────────────────────

    @Test
    void createOrder_validRequest_returns201() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(1L,
                List.of(new OrderItemRequest(1L, 1)));

        when(orderService.createOrder(any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerName").value("John Doe"))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void createOrder_missingCustomerId_returns400() throws Exception {
        String body = """
                { "items": [{ "productId": 1, "quantity": 1 }] }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerId").exists());
    }

    @Test
    void createOrder_emptyItems_returns400() throws Exception {
        String body = """
                { "customerId": 1, "items": [] }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").exists());
    }

    @Test
    void createOrder_itemQuantityZero_returns400() throws Exception {
        String body = """
                { "customerId": 1, "items": [{ "productId": 1, "quantity": 0 }] }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_customerNotFound_returns404() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(99L,
                List.of(new OrderItemRequest(1L, 1)));

        when(orderService.createOrder(any()))
                .thenThrow(new ResourceNotFoundException("Customer", 99L));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found with id: 99"));
    }

    // ─── GET /api/orders/{id} ───────────────────────────────────────────────────

    @Test
    void getOrder_existingId_returns200() throws Exception {
        when(orderService.getOrder(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getOrder_nonExistingId_returns404() throws Exception {
        when(orderService.getOrder(99L))
                .thenThrow(new ResourceNotFoundException("Order", 99L));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found with id: 99"));
    }

    // ─── GET /api/orders ────────────────────────────────────────────────────────

    @Test
    void listOrders_noFilter_returns200WithPage() throws Exception {
        when(orderService.listOrders(eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listOrders_withStatusFilter_returnsFiltered() throws Exception {
        when(orderService.listOrders(eq(OrderStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/orders").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void listOrders_emptyResult_returnsEmptyPage() throws Exception {
        when(orderService.listOrders(eq(OrderStatus.DELIVERED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/orders").param("status", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ─── PATCH /api/orders/{id}/status ─────────────────────────────────────────

    @Test
    void updateStatus_validTransition_returns200() throws Exception {
        OrderResponse processingResponse = new OrderResponse(
                1L, "John Doe", "john@example.com",
                OrderStatus.PROCESSING, new BigDecimal("49.99"),
                LocalDateTime.now(), List.of());

        when(orderService.updateStatus(eq(1L), any())).thenReturn(processingResponse);

        mockMvc.perform(patch("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateStatusRequest(OrderStatus.PROCESSING))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void updateStatus_invalidTransition_returns400() throws Exception {
        when(orderService.updateStatus(eq(1L), any()))
                .thenThrow(new BusinessException("Invalid status transition from PENDING to SHIPPED"));

        mockMvc.perform(patch("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateStatusRequest(OrderStatus.SHIPPED))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid status transition from PENDING to SHIPPED"));
    }

    @Test
    void updateStatus_missingBody_returns400() throws Exception {
        mockMvc.perform(patch("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE /api/orders/{id} ────────────────────────────────────────────────

    @Test
    void cancelOrder_pendingOrder_returns200() throws Exception {
        OrderResponse cancelled = new OrderResponse(
                1L, "John Doe", "john@example.com",
                OrderStatus.CANCELLED, new BigDecimal("49.99"),
                LocalDateTime.now(), List.of());

        when(orderService.cancelOrder(1L)).thenReturn(cancelled);

        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_nonPendingOrder_returns400() throws Exception {
        when(orderService.cancelOrder(1L))
                .thenThrow(new BusinessException("Only PENDING orders can be cancelled. Current status: PROCESSING"));

        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only PENDING orders can be cancelled. Current status: PROCESSING"));
    }

    @Test
    void cancelOrder_notFound_returns404() throws Exception {
        when(orderService.cancelOrder(99L))
                .thenThrow(new ResourceNotFoundException("Order", 99L));

        mockMvc.perform(delete("/api/orders/99"))
                .andExpect(status().isNotFound());
    }
}
