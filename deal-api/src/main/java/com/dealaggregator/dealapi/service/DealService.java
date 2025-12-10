package com.dealaggregator.dealapi.service;
import org.springframework.stereotype.Service;
import com.dealaggregator.dealapi.repository.DealRepository;
import com.dealaggregator.dealapi.entity.Deal;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service class for deal management operations.
 *
 * This service provides business logic for CRUD operations on deals,
 * filtering, and searching functionality. It acts as an intermediary
 * between the controller and repository layers.
 */
@Service
public class DealService {

    private final DealRepository dealRepository;

    /**
     * Constructor for DealService with dependency injection.
     *
     * @param repository Repository for database operations on Deal entities
     */
    public DealService(DealRepository repository) {
        this.dealRepository = repository;
    }

    /**
     * Retrieves all deals from the database.
     *
     * @return List of all Deal objects
     */
    public List<Deal> getAllDeals() {
        return dealRepository.findAll();
    }

    /**
     * Retrieves a specific deal by its ID.
     *
     * @param id Unique identifier of the deal
     * @return Deal object with the specified ID
     * @throws RuntimeException if deal with given ID is not found
     */
    public Deal getDealById(Long id) {
        return dealRepository.findById(id).orElseThrow(() -> new RuntimeException("Deal not found with id: " + id));
    }

    /**
     * Creates a new deal in the database.
     *
     * @param deal Deal object containing all deal information
     * @return Saved Deal object with generated ID and timestamps
     */
    public Deal createDeal(Deal deal) {
        return dealRepository.save(deal);
    }

    /**
     * Updates an existing deal with new information.
     *
     * Retrieves the existing deal and updates all its fields with the
     * provided values from dealDetails.
     *
     * @param id Unique identifier of the deal to update
     * @param dealDetails Deal object containing updated information
     * @return Updated Deal object
     * @throws RuntimeException if deal with given ID is not found
     */
    public Deal updateDeal(Long id, Deal dealDetails) {
        Deal existingDeal = getDealById(id);

        // Update all fields with new values
        existingDeal.setTitle(dealDetails.getTitle());
        existingDeal.setPrice(dealDetails.getPrice());
        existingDeal.setOriginalPrice(dealDetails.getOriginalPrice());
        existingDeal.setDiscountPercentage(dealDetails.getDiscountPercentage());
        existingDeal.setVendor(dealDetails.getVendor());
        existingDeal.setDealUrl(dealDetails.getDealUrl());
        existingDeal.setCategory(dealDetails.getCategory());
        existingDeal.setDealType(dealDetails.getDealType());
        existingDeal.setDescription(dealDetails.getDescription());
        return dealRepository.save(existingDeal);
    }

    /**
     * Deletes a deal from the database.
     *
     * @param id Unique identifier of the deal to delete
     */
    public void deleteDeal(Long id) {
        dealRepository.deleteById(id);
    }

    /**
     * Retrieves all deals in a specific category.
     *
     * @param category Category name to filter by
     * @return List of Deal objects in the specified category
     */
    public List<Deal> getDealsByCategory(String category) {
        return dealRepository.findByCategory(category);
    }

    /**
     * Retrieves all deals from a specific vendor.
     *
     * @param vendor Vendor name to filter by
     * @return List of Deal objects from the specified vendor
     */
    public List<Deal> getDealsByVendor(String vendor) {
        return dealRepository.findByVendor(vendor);
    }

    /**
     * Retrieves all deals with price less than the specified amount.
     *
     * @param price Maximum price threshold
     * @return List of Deal objects with price below the threshold
     */
    public List<Deal> getDealsCheaperThan(BigDecimal price) {
        return dealRepository.findByPriceLessThan(price);
    }

    /**
     * Searches for deals by keyword in the title.
     *
     * Performs a case-insensitive search for deals containing the
     * specified keyword anywhere in their title.
     *
     * @param keyword Search term to look for in deal titles
     * @return List of Deal objects matching the search criteria
     */
    public List<Deal> searchDeals(String keyword) {
        return dealRepository.findByTitleContainingIgnoreCase(keyword);
    }
}