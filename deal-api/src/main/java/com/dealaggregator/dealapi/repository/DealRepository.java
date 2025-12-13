package com.dealaggregator.dealapi.repository;
import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dealaggregator.dealapi.entity.Deal;

/**
 * Repository interface for Deal entity database operations.
 *
 * This interface extends JpaRepository to provide standard CRUD operations
 * and defines custom query methods for filtering and searching deals.
 * Spring Data JPA automatically implements these methods based on naming conventions.
 */
@Repository
public interface DealRepository extends JpaRepository<Deal, Long> {

    /**
     * Finds all deals in a specific category.
     *
     * @param category Category name to search for
     * @return List of deals matching the specified category
     */
    List<Deal> findByCategory(String category);

    /**
     * Finds all deals from a specific vendor.
     *
     * @param vendor Vendor name to search for
     * @return List of deals from the specified vendor
     */
    List<Deal> findByVendor(String vendor);

    /**
     * Searches for deals with titles containing the specified keyword.
     * Search is case-insensitive.
     *
     * @param keyword Search term to look for in deal titles
     * @return List of deals with titles containing the keyword
     */
    List<Deal> findByTitleContainingIgnoreCase(String keyword);

    /**
     * Finds all deals with price below the specified amount.
     *
     * @param price Maximum price threshold
     * @return List of deals with price less than the specified amount
     */
    List<Deal> findByPriceLessThan(BigDecimal price);
    
    
}