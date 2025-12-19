package com.dealaggregator.dealapi.entity;

import java.time.LocalDate;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "legs")
public class Leg {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id")
    private Strategy strategy;

    private String optionType;
    private Double strikePrice;
    private LocalDate expiration;
    private Double entryPrice;

    private Integer quantity;

    public Leg() {
    }

    public Leg(String optionType, Double strikePrice, LocalDate expiration, Double entryPrice, Integer quantity) {
        this.optionType = optionType;
        this.strikePrice = strikePrice;
        this.expiration = expiration;
        this.entryPrice = entryPrice;
        this.quantity = quantity;
    }
}