package com.ecommerce.order.mgmt.controller;

import com.ecommerce.order.mgmt.dto.request.LoginRequest;
import com.ecommerce.order.mgmt.dto.request.RegisterRequest;
import com.ecommerce.order.mgmt.dto.response.AuthResponse;
import com.ecommerce.order.mgmt.entity.Customer;
import com.ecommerce.order.mgmt.entity.User;
import com.ecommerce.order.mgmt.enums.Role;
import com.ecommerce.order.mgmt.exception.BusinessException;
import com.ecommerce.order.mgmt.repository.CustomerRepository;
import com.ecommerce.order.mgmt.repository.UserRepository;
import com.ecommerce.order.mgmt.security.JwtService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    /**
     * POST /api/auth/login
     * Authenticates a user and returns a JWT token.
     */
    @PostMapping("/login")
    @RateLimiter(name = "auth")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String token = jwtService.generateToken(userDetails);
        // Extract role directly from authorities — avoids an extra DB round-trip
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .orElse("USER");
        return ResponseEntity.ok(new AuthResponse(token, userDetails.getUsername(), role));
    }

    /**
     * POST /api/auth/register
     * Registers a new USER-role account and returns a JWT token.
     * Admin accounts are seeded via data.sql only.
     */
    @PostMapping("/register")
    @RateLimiter(name = "auth")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .email(request.email())
                .role(Role.USER)
                .build();

        User saved = userRepository.save(user);

        // Auto-create a matching Customer so the USER can place orders immediately
        if (customerRepository.findByEmail(request.email()).isEmpty()) {
            customerRepository.save(Customer.builder()
                    .name(request.username())
                    .email(request.email())
                    .build());
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(saved.getUsername());
        String token = jwtService.generateToken(userDetails);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, saved.getUsername(), saved.getRole().name()));
    }
}
