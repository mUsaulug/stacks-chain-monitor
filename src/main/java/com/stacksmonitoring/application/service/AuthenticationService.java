package com.stacksmonitoring.application.service;

import com.stacksmonitoring.api.dto.request.LoginRequest;
import com.stacksmonitoring.api.dto.request.RegisterRequest;
import com.stacksmonitoring.api.dto.response.AuthenticationResponse;
import com.stacksmonitoring.domain.model.user.User;
import com.stacksmonitoring.domain.repository.UserRepository;
import com.stacksmonitoring.domain.valueobject.UserRole;
import com.stacksmonitoring.infrastructure.config.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for handling user authentication operations.
 * Manages user registration, login, and JWT token generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    @Value("${security.jwt.expiration-ms}")
    private Long jwtExpirationMs;

    /**
     * Register a new user.
     */
    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        // Check if user already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        // Create new user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(UserRole.USER);
        user.setActive(true);

        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        // Generate JWT token
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtTokenService.generateToken(userDetails, user.getRole().name());

        return AuthenticationResponse.builder()
                .token(token)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .expiresIn(jwtExpirationMs)
                .build();
    }

    /**
     * Authenticate user and generate JWT token.
     */
    @Transactional(readOnly = true)
    public AuthenticationResponse login(LoginRequest request) {
        // Authenticate user
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Load user details
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.getEmail()));

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());

        // Generate JWT token
        String token = jwtTokenService.generateToken(userDetails, user.getRole().name());

        log.info("User logged in: {}", user.getEmail());

        return AuthenticationResponse.builder()
                .token(token)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .expiresIn(jwtExpirationMs)
                .build();
    }
}
