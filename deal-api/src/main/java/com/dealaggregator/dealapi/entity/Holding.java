package com.dealaggregator.dealapi.entity;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "holdings")
public class Holding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String discordUserId; // "Teerth#1234"
    private String ticker; // "NVDA"
    private String type; // "CALL" or "PUT"
    private Double strikePrice; // 150.0
    private LocalDate expiration; // 2026-01-16
    private Double buyPrice; // 1.50 (Price paid)

    // --- CONSTRUCTORS ---

    // 1. Empty Constructor (Required by Spring/JPA to work)
    public Holding() {
    }

    // 2. Full Constructor (Used by your Service to create new rows)
    public Holding(String discordUserId, String ticker, String type, Double strikePrice, LocalDate expiration,
            Double buyPrice) {
        this.discordUserId = discordUserId;
        this.ticker = ticker;
        this.type = type;
        this.strikePrice = strikePrice;
        this.expiration = expiration;
        this.buyPrice = buyPrice;
    }

    // Explicit Getters for usage in Service (Redundant if Lombok works, but safe
    // for fixing build)
    public String getTicker() {
        return ticker;
    }

    public Double getStrikePrice() {
        return strikePrice;
    }

    public String getType() {
        return type;
    }

    public java.time.LocalDate getExpiration() {
        return expiration;
    }

    public Double getBuyPrice() {
        return buyPrice;
    }

    public String getDiscordUserId() {
        return discordUserId;
    }

}