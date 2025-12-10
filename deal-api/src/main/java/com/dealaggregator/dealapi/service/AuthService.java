package com.dealaggregator.dealapi.service;

import org.springframework.stereotype.Service;
import com.dealaggregator.dealapi.repository.UserRepository;
import com.dealaggregator.dealapi.entity.User;

/**
 * Service class for user authentication and registration operations.
 *
 * This service handles business logic for user account creation and
 * authentication. It validates user credentials, checks for duplicates,
 * and manages user data through the UserRepository.
 *
 * NOTE: Current implementation stores passwords in plain text. For production,
 * passwords should be hashed using BCrypt or similar secure hashing algorithms.
 */
@Service
public class AuthService {
    private final UserRepository userRepository;

    /**
     * Constructor for AuthService with dependency injection.
     *
     * @param userRepository Repository for database operations on User entities
     */
    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Registers a new user in the system.
     *
     * Creates a new user account after validating that the username and email
     * are unique. Stores the user information in the database.
     *
     * @param username Desired username for the new account (must be unique)
     * @param password Password for the new account (currently stored as plain text)
     * @param email Email address for the new account (must be unique)
     * @return User object representing the newly created user
     * @throws RuntimeException if username already exists
     * @throws RuntimeException if email already exists
     */
    public User registerUser(String username, String password, String email) {
        // Check if username is already taken
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username is already taken!");
        }

        // Check if email is already in use
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email is already in use!");
        }

        // Create and save new user
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setEmail(email);
        newUser.setPassword(password);
        return userRepository.save(newUser);
    }

    /**
     * Authenticates a user with provided credentials.
     *
     * Validates the username and password combination. Returns the user
     * object if credentials are valid.
     *
     * NOTE: This is a basic authentication implementation. For production,
     * use secure password hashing (BCrypt) and consider implementing JWT tokens.
     *
     * @param username Username for authentication
     * @param password Password for authentication (currently plain text comparison)
     * @return User object if authentication successful
     * @throws RuntimeException if username not found or password is incorrect
     */
    public User login(String username, String password) {
        // Find user by username
        User user  = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("Invalid credentials"));

        // Verify password (plain text comparison - should be hashed in production)
        if (!user.getPassword().equals(password)) {
            throw new RuntimeException("Invalid credentials!");
        }
        return user;
    }

}