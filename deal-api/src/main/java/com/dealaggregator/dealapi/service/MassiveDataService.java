package com.dealaggregator.dealapi.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;

/**
 * MassiveDataService - Fetches real options data from Massive.com API
 * 
 * We will build this step by step!
 */
@Service
public class MassiveDataService {

    // Step 2: Dependencies (fields that store our tools)
    private final RestClient restClient; // HTTP client for API calls
    private final String baseUrl; // API base URL (e.g., https://api.massive.com/v3)
    private final String apiKey; // API Key for authentication

    // Step 3: Constructor - Spring calls this when creating the service
    public MassiveDataService(
            RestClient.Builder restClientBuilder,
            @Value("${massive.api.url}") String baseUrl,
            @Value("${massive.api.key}") String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey) // Or "X-API-KEY", depending on the API
                .build();
    }

    // ← ADD THE TEST METHOD HERE ←
    @PostConstruct
    public void testApiResponse() {
        String endpoint = "/snapshot/options/SPX?limit=5";

        try {
            String rawResponse = restClient
                    .get()
                    .uri(endpoint)
                    .retrieve()
                    .body(String.class);

            System.out.println("=== RAW API RESPONSE ===");
            System.out.println(rawResponse);
            System.out.println("========================");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
