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

    public SchwabToken() {
    }

    public SchwabToken(String id, String refreshToken, String accessToken, Long updatedAt) {
        this.id = id;
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
