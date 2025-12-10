package com.dealaggregator.dealapi.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.dealaggregator.dealapi.entity.User;
import java.util.Optional;

/**
 * Repository interface for User entity database operations.
 *
 * This interface extends JpaRepository to provide standard CRUD operations
 * and defines custom query methods for user authentication and validation.
 * Spring Data JPA automatically implements these methods based on naming conventions.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their username.
     *
     * @param username Username to search for
     * @return Optional containing the user if found, empty otherwise
     */
    Optional<User> findByUsername(String username);

    /**
     * Finds a user by their email address.
     *
     * @param email Email address to search for
     * @return Optional containing the user if found, empty otherwise
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks if a user with the given username exists in the database.
     *
     * @param username Username to check for existence
     * @return true if a user with this username exists, false otherwise
     */
    Boolean existsByUsername(String username);

    /**
     * Checks if a user with the given email exists in the database.
     *
     * @param email Email address to check for existence
     * @return true if a user with this email exists, false otherwise
     */
    Boolean existsByEmail(String email);
}
