package com.dealaggregator.dealapi.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MassiveDataService - Fetches real options data from Massive.com API
 */
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
     * DTO for Option Snapshot used by DiscordBotService
     */
    @lombok.Data
    public static class OptionSnapshot {
        private double bid;
        private double ask;
        private int volume;
        private int openInterest;
        private double strike;

        public OptionSnapshot(double bid, double ask, int volume, int openInterest, double strike) {
            this.bid = bid;
            this.ask = ask;
            this.volume = volume;
            this.openInterest = openInterest;
            this.strike = strike;
        }
    }

    /**
     * Fetch a specific option contract snapshot.
     * Used by /liquidity command.
     */
    public Optional<OptionSnapshot> getOptionSnapshot(String ticker, double strike, String type, int days) {
        // Since Massive API gives us a chain, we might need to fetch the chain and
        // filter locally
        // or use a specific endpoint if one exists. For now, fetch chain and filter.

        List<OptionContract> chain = fetchOptionsChain(ticker);

        // Filter logic
        for (OptionContract contract : chain) {
            if (contract.details() != null) {
                Double cStrike = contract.details().strike_price();
                String cType = contract.details().contract_type();
                // We'd also check expiration days ideally.
                // For simplicity, just matching strike and type for now.

                if (cStrike != null && cStrike == strike &&
                        cType != null && cType.equalsIgnoreCase(type)) {

                    double bid = contract.last_quote() != null && contract.last_quote().bid() != null
                            ? contract.last_quote().bid()
                            : 0.0;
                    double ask = contract.last_quote() != null && contract.last_quote().ask() != null
                            ? contract.last_quote().ask()
                            : 0.0;
                    int vol = contract.day() != null && contract.day().volume() != null ? contract.day().volume() : 0;
                    int oi = contract.day() != null && contract.day().open_interest() != null
                            ? contract.day().open_interest()
                            : 0;

                    return Optional.of(new OptionSnapshot(bid, ask, vol, oi, strike));
                }
            }
        }

        return Optional.empty();
    }

    public List<OptionContract> fetchOptionsChain(String ticker) {
        String endpoint = "/snapshot/options/" + ticker + "?limit=250";
        try {
            MassiveApiResponse response = restClient
                    .get()
                    .uri(endpoint)
                    .retrieve()
                    .body(MassiveApiResponse.class);

            if (response == null || response.results() == null) {
                return new ArrayList<>();
            }
            return response.results();

        } catch (Exception e) {
            System.err.println("Error fetching options: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @PostConstruct
    public void testApiResponse() {
        // Optional startup test
        System.out.println("MassiveDataService initialized.");
    }
}
