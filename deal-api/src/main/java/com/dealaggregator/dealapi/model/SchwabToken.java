package com.dealaggregator.dealapi.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "schwab_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchwabToken {

    @Id
    private String id; // We'll use a constant ID like "default" to keep it simple (singleton row)

    private String refreshToken;
    private String accessToken;
    private Long updatedAt;
}
