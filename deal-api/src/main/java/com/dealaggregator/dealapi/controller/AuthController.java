package com.dealaggregator.dealapi.controller;

import com.dealaggregator.dealapi.entity.User;
import com.dealaggregator.dealapi.service.AuthService;

import org.springframework.web.bind.annotation.*;
import lombok.Data;

/**
 * REST Controller for user authentication endpoints.
 *
 * This controller handles user registration and login operations through
 * RESTful API endpoints. All authentication-related requests are mapped
 * under the /api/auth base path.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    /**
     * Constructor for AuthController with dependency injection.
     *
     * @param authService Service layer for authentication operations
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Endpoint for user registration.
     *
     * Creates a new user account with the provided credentials and stores
     * it in the database. Validates that username and email are unique.
     *
     * @param request RegisterRequest containing username, password, and email
     * @return User object representing the newly created user
     * @throws RuntimeException if username or email already exists
     */
    @PostMapping("/register")
    public User register(@RequestBody RegisterRequest request) {
        return authService.registerUser(request.getUsername(), request.getPassword(), request.getEmail());
    }

    /**
     * Endpoint for user login.
     *
     * Authenticates a user with the provided credentials and returns
     * the user object if authentication is successful.
     *
     * @param request LoginRequest containing username and password
     * @return User object if credentials are valid
     * @throws RuntimeException if credentials are invalid
     */
    @PostMapping("/login")
    public User login(@RequestBody LoginRequest request) {
        return authService.login(request.getUsername(),request.getPassword());
    }

    /**
     * Data Transfer Object for user registration requests.
     *
     * Contains all required fields for creating a new user account.
     */
    @Data
    public static class RegisterRequest {
        /**
         * Password for the new user account
         */
        private String password;

        /**
         * Unique username for the new user account
         */
        private String username;

        /**
         * Unique email address for the new user account
         */
        private String email;
    }

    /**
     * Data Transfer Object for user login requests.
     *
     * Contains the credentials needed for user authentication.
     */
    @Data
    public static class LoginRequest {
        /**
         * Username for authentication
         */
        private String username;

        /**
         * Password for authentication
         */
        private String password;
    }

}
