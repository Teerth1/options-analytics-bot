package com.dealaggregator.dealapi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "strategies")
public class Strategy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String strategy;
    private String ticker;

    private LocalDateTime openedAt = LocalDateTime.now();

    private String status = "OPEN"; // "OPEN" or "CLOSED"

    // This creates the 1-to-many relationship with Leg
    @OneToMany(mappedBy = "strategy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Leg> legs = new ArrayList<>();

    public Strategy() {
    }

    public Strategy(String userId, String strategy, String ticker) {
        this.userId = userId;
        this.strategy = strategy;
        this.ticker = ticker;
    }
}
