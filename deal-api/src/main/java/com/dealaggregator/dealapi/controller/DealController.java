package com.dealaggregator.dealapi.controller;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dealaggregator.dealapi.entity.Deal;
import com.dealaggregator.dealapi.service.DealScraperService;
import com.dealaggregator.dealapi.service.DealService;

/**
 * REST Controller for deal management endpoints.
 *
 * This controller provides RESTful API endpoints for CRUD operations on deals,
 * searching deals, filtering by category, and triggering manual scraping.
 * All deal-related requests are mapped under the /api/deals base path.
 */
@RestController
@RequestMapping("/api/deals")
public class DealController {
    private final DealService dealService;
    private final DealScraperService dealScraperService;

    /**
     * Constructor for DealController with dependency injection.
     *
     * @param dealService Service layer for deal operations
     * @param dealScrapperService Service layer for web scraping operations
     */
    public DealController(DealService dealService, DealScraperService dealScrapperService){
        this.dealScraperService = dealScrapperService;
        this.dealService = dealService;
    }

    /**
     * Retrieves all deals from the database.
     *
     * @return List of all Deal objects
     */
    @GetMapping
    public List<Deal> getAllDeals() {
        return dealService.getAllDeals();
    }

    /**
     * Retrieves a specific deal by its ID.
     *
     * @param id Unique identifier of the deal
     * @return Deal object with the specified ID
     * @throws RuntimeException if deal with given ID is not found
     */
    @GetMapping("/{id}")
    public Deal getDealById(@PathVariable Long id) {
        return dealService.getDealById(id);
    }

    /**
     * Manually triggers the Reddit scraping process for testing purposes.
     *
     * This endpoint initiates a scraping operation to fetch deals from
     * the r/buildapcsales subreddit. Results are logged to the console.
     *
     * @return String message indicating where to check for scraping results
     */
    @GetMapping("/scrape-test")
    public String testScrape() {
        dealScraperService.scrapeReddit();
        return "Check console for results";
    }

    /**
     * Creates a new deal in the database.
     *
     * @param deal Deal object containing all deal information
     * @return Saved Deal object with generated ID
     */
    @PostMapping
    public Deal createDeal(@RequestBody Deal deal) {
        return dealService.createDeal(deal);
    }

    /**
     * Updates an existing deal with new information.
     *
     * @param id Unique identifier of the deal to update
     * @param dealDetails Deal object containing updated information
     * @return Updated Deal object
     * @throws RuntimeException if deal with given ID is not found
     */
    @PutMapping("/{id}")
    public Deal updateDeal(@PathVariable Long id, @RequestBody Deal dealDetails) {
        return dealService.updateDeal(id, dealDetails);
    }

    /**
     * Deletes a deal from the database.
     *
     * @param id Unique identifier of the deal to delete
     */
    @DeleteMapping("/{id}")
    public void deleteDeal(@PathVariable Long id) {
        dealService.deleteDeal(id);
    }

    /**
     * Retrieves all deals in a specific category.
     *
     * @param category Category name to filter by (e.g., "GPU", "CPU", "Monitor")
     * @return List of Deal objects in the specified category
     */
    @GetMapping("/category/{category}")
    public List<Deal> getDealsByCategory(@PathVariable String category) {
        return dealService.getDealsByCategory(category);
    }

    /**
     * Searches for deals by keyword in the title.
     *
     * Performs a case-insensitive search for deals containing the
     * specified keyword in their title.
     *
     * @param keyword Search term to look for in deal titles
     * @return List of Deal objects matching the search criteria
     */
    @GetMapping("/search")
    public List<Deal> searchDeals(@RequestParam String keyword) {
        return dealService.searchDeals(keyword);
    }


}