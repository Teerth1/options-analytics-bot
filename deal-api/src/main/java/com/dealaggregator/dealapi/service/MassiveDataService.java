package com.dealaggregator.dealapi.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.dealaggregator.dealapi.service.YahooOptionsResponse.Contract;

import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * MassiveDataService (Refactored for Yahoo Finance)
 * Fetches real-time option data from Yahoo Finance's unofficial API.
 */
@Service
public class MassiveDataService {

    private final RestClient restClient;
    // Unofficial Yahoo Finance Options API
    private static final String YAHOO_BASE_URL = "https://query1.finance.yahoo.com/v7/finance/options/";

    public MassiveDataService(RestClient.Builder restClientBuilder) {
        // Yahoo requires a User-Agent to avoid 403 Forbidden
        this.restClient = restClientBuilder
                .baseUrl(YAHOO_BASE_URL)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
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
     * Fetch a specific option contract snapshot using Yahoo Finance.
     */
    public Optional<OptionSnapshot> getOptionSnapshot(String ticker, double strike, String type, int days) {
        try {
            // 1. Fetch metadata to get expiration dates
            // URL: /options/{ticker}
            YahooOptionsResponse metadata = restClient.get()
                    .uri(ticker)
                    .retrieve()
                    .body(YahooOptionsResponse.class);

            if (metadata == null || metadata.optionChain == null || metadata.optionChain.result.isEmpty()) {
                System.out.println("DEBUG: No data found for ticker " + ticker);
                return Optional.empty();
            }

            List<Long> expirations = metadata.optionChain.result.get(0).expirationDates;
            if (expirations == null || expirations.isEmpty()) {
                System.out.println("DEBUG: No expiration dates found for " + ticker);
                return Optional.empty();
            }

            // 2. Find the closest expiration date to the user's request
            long bestTimestamp = findClosestExpiration(expirations, days);
            System.out.println("DEBUG: Requested " + days + " days. Using expiration timestamp: " + bestTimestamp
                    + " (" + Instant.ofEpochSecond(bestTimestamp).atZone(ZoneId.systemDefault()).toLocalDate() + ")");

            // 3. Fetch the specific chain for that date
            // URL: /options/{ticker}?date={timestamp}
            YahooOptionsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(ticker)
                            .queryParam("date", bestTimestamp)
                            .build())
                    .retrieve()
                    .body(YahooOptionsResponse.class);

            if (response == null || response.optionChain == null || response.optionChain.result.isEmpty()) {
                return Optional.empty();
            }

            YahooOptionsResponse.OptionData chainData = response.optionChain.result.get(0).options.get(0);
            List<Contract> contracts = type.equalsIgnoreCase("call") ? chainData.calls : chainData.puts;

            if (contracts == null) {
                System.out.println("DEBUG: No " + type + " contracts found for this date.");
                return Optional.empty();
            }

            // 4. Find the matching strike
            for (Contract contract : contracts) {
                // Fuzzy match for strike price (handle floating point errors)
                if (Math.abs(contract.strike - strike) < 0.01) {
                    System.out.println(
                            "DEBUG: Found match! Strike: " + contract.strike + " Price: " + contract.lastPrice);
                    return Optional.of(new OptionSnapshot(
                            contract.bid,
                            contract.ask,
                            contract.volume,
                            contract.openInterest,
                            contract.strike));
                }
            }

            System.out.println("DEBUG: Strike " + strike + " not found in chain for " + ticker);

        } catch (Exception e) {
            System.err.println("Error fetching Yahoo data: " + e.getMessage());
            e.printStackTrace();
        }

        return Optional.empty();
    }

    /**
     * Helper to find the expiration timestamp closest to 'days' from now.
     */
    private long findClosestExpiration(List<Long> expirations, int targetDays) {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = today.plusDays(targetDays);

        long bestTimestamp = expirations.get(0);
        long minDiff = Long.MAX_VALUE;

        for (Long ts : expirations) {
            LocalDate expDate = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalDate();
            long diff = Math.abs(ChronoUnit.DAYS.between(targetDate, expDate));

            if (diff < minDiff) {
                minDiff = diff;
                bestTimestamp = ts;
            }
        }
        return bestTimestamp;
    }

    @PostConstruct
    public void testYahooConnection() {
        System.out.println("YahooFinance Service initialized.");
    }
}
