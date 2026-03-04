package com.ecommerce.order.mgmt.security;

import com.ecommerce.order.mgmt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Bridges the security context and the business layer.
 * Keeps SecurityContextHolder access out of OrderService.
 */
@Service
@RequiredArgsConstructor
public class SecurityService {

    private final UserRepository userRepository;

    /** Returns the email address of the currently authenticated user, if any. */
    public Optional<String> getCurrentUserEmail() {
        String username = getCurrentUsername().orElse(null);
        if (username == null) return Optional.empty();
        return userRepository.findByUsername(username).map(u -> u.getEmail());
    }

    /** True when the current user holds the ADMIN role. */
    public boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private Optional<String> getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(auth.getName());
    }
}
