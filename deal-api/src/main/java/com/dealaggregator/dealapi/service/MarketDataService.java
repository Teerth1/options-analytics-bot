package com.dealaggregator.dealapi.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import com.dealaggregator.dealapi.dto.MassiveApiResponse;
import com.dealaggregator.dealapi.dto.OptionContract;

import java.util.ArrayList;
import java.util.List;

@Service
public class MassiveDataService {

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;

    public MassiveDataService(
            RestClient.Builder restClientBuilder,
            @Value("${massive.api.url}") String baseUrl,
            @Value("${massive.api.key}") String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /**
     * Step 4: Fetch options chain for a ticker (e.g., "SPX")
     * 
     * This method:
     * 1. Makes GET request to /snapshot/options/{ticker}
     * 2. Parses JSON response into Java objects
     * 3. Returns list of option contracts
     */
    public List<OptionContract> fetchOptionsChain(String ticker) {
        // Build the endpoint URL
        String endpoint = "/snapshot/options/" + ticker + "?limit=250";

        System.out.println("Fetching options for: " + ticker);
        System.out.println("URL: " + baseUrl + endpoint);

        try {
            // Make the API call
            MassiveApiResponse response = restClient
                    .get()
                    .uri(endpoint)
                    .retrieve()
                    .body(MassiveApiResponse.class);

            // Check if we got data
            if (response == null || response.results() == null) {
                System.out.println("No data returned from API");
                return new ArrayList<>();
            }

            System.out.println("Received " + response.results().size() + " contracts");

            // Return the contracts
            return response.results();

        } catch (Exception e) {
            System.err.println("Error fetching options: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}