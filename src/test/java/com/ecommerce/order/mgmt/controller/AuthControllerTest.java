package com.ecommerce.order.mgmt.controller;

import com.ecommerce.order.mgmt.config.SecurityConfig;
import com.ecommerce.order.mgmt.dto.request.LoginRequest;
import com.ecommerce.order.mgmt.dto.request.RegisterRequest;
import com.ecommerce.order.mgmt.entity.User;
import com.ecommerce.order.mgmt.enums.Role;
import com.ecommerce.order.mgmt.exception.GlobalExceptionHandler;
import com.ecommerce.order.mgmt.repository.CustomerRepository;
import com.ecommerce.order.mgmt.repository.UserRepository;
import com.ecommerce.order.mgmt.security.CustomUserDetailsService;
import com.ecommerce.order.mgmt.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("null") // suppress Eclipse false-positive null warnings on MockMvc builder chains
@WebMvcTest(AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AuthenticationManager authenticationManager;
    @MockitoBean JwtService jwtService;
    @MockitoBean CustomUserDetailsService userDetailsService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean CustomerRepository customerRepository;
    @MockitoBean PasswordEncoder passwordEncoder;

    private final org.springframework.security.core.userdetails.User mockAdminDetails =
            new org.springframework.security.core.userdetails.User(
                    "admin", "encoded",
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    // ─── POST /api/auth/login ────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("admin", "admin123"));
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(mockAdminDetails);
        when(jwtService.generateToken(mockAdminDetails)).thenReturn("jwt-token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void login_missingUsername_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "password": "admin123" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void login_missingPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // ─── POST /api/auth/register ─────────────────────────────────────────────

    @Test
    void register_validRequest_returns201WithToken() throws Exception {
        RegisterRequest request = new RegisterRequest("newuser", "password123", "new@example.com");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(customerRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        User saved = Objects.requireNonNull(User.builder()
                .id(10L).username("newuser").password("encoded")
                .email("new@example.com").role(Role.USER).build());
        when(userRepository.save(any())).thenReturn(saved);

        var newUserDetails = new org.springframework.security.core.userdetails.User(
                "newuser", "encoded", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(userDetailsService.loadUserByUsername("newuser")).thenReturn(newUserDetails);
        when(jwtService.generateToken(newUserDetails)).thenReturn("new-jwt-token");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("new-jwt-token"))
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void register_duplicateUsername_returns400() throws Exception {
        when(userRepository.existsByUsername("admin")).thenReturn(true);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("admin", "password123", "other@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username already taken: admin"));
    }

    @Test
    void register_duplicateEmail_returns400() throws Exception {
        when(userRepository.existsByUsername("someone")).thenReturn(false);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("someone", "password123", "admin@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already registered: admin@example.com"));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("newuser", "password123", "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("newuser", "short", "new@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }
}
