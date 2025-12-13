package com.dealaggregator.dealapi.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dealaggregator.dealapi.entity.Holding;

/**
 * Repository interface for Holding entity database operations.
 *
 * This interface extends JpaRepository to provide standard CRUD operations
 * for managing user portfolio holdings.
 * Spring Data JPA automatically implements these methods.
 */
@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    /**
     * Finds all holdings for a specific user.
     *
     * @param userId User ID to search for
     * @return List of holdings belonging to the specified user
     */
    List<Holding> findByDiscordUserId(String userId);

    /**
     * Finds all holdings for a specific ticker symbol.
     *
     * @param ticker Stock ticker symbol
     * @return List of holdings with the specified ticker
     */
    List<Holding> findByTicker(String ticker);


    /**
     * Deletes all holdings for a specific user and ticker symbol.
     *
     * @param userId User ID to filter by
     * @param ticker Stock ticker symbol to filter by
     * @return Number of holdings deleted
     */
    int deleteByDiscordUserIdAndTicker(String userId, String ticker);

}
