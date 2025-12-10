package com.dealaggregator.dealapi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity class representing a user in the system.
 *
 * This class maps to the 'users' table in the database and stores user account
 * information including credentials, role, and timestamps. It supports user
 * authentication and authorization features.
 *
 * Uses Lombok annotations for automatic generation of getters, setters, and constructors.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    /**
     * Unique identifier for the user (primary key).
     * Auto-generated using database identity strategy.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User role for authorization purposes.
     * Default value is "USER" for regular users.
     * Other roles might include "ADMIN", "MODERATOR", etc.
     */
    @Column(nullable = false)
    private String role = "USER";

    /**
     * Unique username for the user account.
     * Used for login and identification within the system.
     */
    @Column(nullable = false, unique = true)
    private String username;

    /**
     * Unique email address for the user account.
     * Used for login and account recovery.
     */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Hashed password for user authentication.
     * NOTE: Currently stored as plain text - should be hashed using BCrypt or similar.
     */
    @Column(nullable = false)
    private String password;

    /**
     * Timestamp when the user account was created.
     * Automatically set on entity creation.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the user account was last updated.
     * Automatically updated on any entity modification.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * JPA callback method executed before persisting a new user.
     * Sets the createdAt and updatedAt timestamps to the current time.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * JPA callback method executed before updating an existing user.
     * Updates the updatedAt timestamp to the current time.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


}