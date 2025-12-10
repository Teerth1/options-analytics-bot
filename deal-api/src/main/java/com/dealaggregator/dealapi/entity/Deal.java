package com.dealaggregator.dealapi.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity class representing a deal in the system.
 *
 * This class maps to the 'deals' table in the database and stores information
 * about product deals from various online retailers. It includes details such as
 * pricing, vendor information, categories, and automatic timestamp management.
 *
 * Uses Lombok annotations for automatic generation of getters, setters, and constructors.
 */
@Entity
@Table(name = "deals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Deal{
    /**
     * Unique identifier for the deal (primary key).
     * Auto-generated using database identity strategy.
     */
    @Id
    @GeneratedValue(strategy=jakarta.persistence.GenerationType.IDENTITY)
    private Long id ;

    /**
     * Title/name of the deal.
     * Stored as TEXT to handle long titles from scraped content.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    /**
     * Current price of the product in the deal.
     * Required field stored as BigDecimal for precise currency calculations.
     */
    @Column(nullable = false)
    private BigDecimal price;

    /**
     * Original price before discount (if available).
     * Optional field used to calculate savings.
     */
    private BigDecimal originalPrice;

    /**
     * Percentage discount from original price (if available).
     * Optional field to display savings information.
     */
    private Integer discountPercentage;

    /**
     * Name of the vendor/retailer offering the deal.
     * Examples: Amazon, Newegg, Best Buy, Walmart, etc.
     */
    @Column(nullable = false)
    private String vendor;

    /**
     * URL link to the actual deal page.
     * Stored as TEXT to handle long URLs.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String dealUrl;

    /**
     * Category of the product.
     * Examples: GPU, CPU, Monitor, Storage, etc.
     */
    @Column(nullable = false)
    private String category;

    /**
     * Type of deal.
     * Examples: Online, In-Store, Clearance, etc.
     */
    @Column(nullable = false)
    private String dealType;

    /**
     * Additional description or details about the deal.
     * Optional field stored as TEXT for longer descriptions.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Timestamp when the deal was created in the system.
     * Automatically set on entity creation.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when the deal was last updated.
     * Automatically updated on any entity modification.
     */
    private LocalDateTime updatedAt;

    /**
     * Optional expiration timestamp for time-limited deals.
     */
    private LocalDateTime expiresAt;

    /**
     * JPA callback method executed before persisting a new deal.
     * Sets the createdAt and updatedAt timestamps to the current time.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * JPA callback method executed before updating an existing deal.
     * Updates the updatedAt timestamp to the current time.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}