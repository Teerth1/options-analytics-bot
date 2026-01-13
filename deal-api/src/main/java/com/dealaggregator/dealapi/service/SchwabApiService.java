package com.dealaggregator.dealapi.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service to fetch SPX options data from Schwab API.
 * Provides real-time bid/ask prices for SPX options.
 */
@Service
public class SchwabApiService {

    private static final Logger logger = LoggerFactory.getLogger(SchwabApiService.class);
    private static final String BASE_URL = "https://api.schwabapi.com/marketdata/v1";
    private static final String TOKEN_URL = "https://api.schwabapi.com/v1/oauth/token";

    @Value("${schwab.client.id:}")
    private String clientId;

    @Value("${schwab.client.secret:}")
    private String clientSecret;

    @Value("${schwab.refresh.token:}")
    private String refreshToken;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String accessToken;
    private long tokenExpiresAt = 0;

    public SchwabApiService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Refresh the access token using the separate refresh token.
     * This ensures we always have a valid session to talk to Schwab.
     * 
     * Logic:
     * 1. Check if current token is expired.
     * 2. If yes, POST to Schwab's OAuth endpoint.
     * 3. Update the local access token.
     */
    private synchronized String refreshAccessToken() {
        if (System.currentTimeMillis() < tokenExpiresAt && accessToken != null) {
            return accessToken; // Token still valid
        }

        try {
            // Trim inputs to remove accidental whitespace
            String safeClientId = clientId.trim();
            String safeClientSecret = clientSecret.trim();
            String safeRefreshToken = refreshToken.trim();

            if (safeClientId.isEmpty() || safeClientSecret.isEmpty() || safeRefreshToken.isEmpty()) {
                logger.error(
                        "Schwab credentials missing. Ensure SCHWAB_CLIENT_ID, SCHWAB_CLIENT_SECRET, and SCHWAB_REFRESH_TOKEN are set.");
                return null;
            }

            String auth = Base64.getEncoder().encodeToString(
                    (safeClientId + ":" + safeClientSecret).getBytes(StandardCharsets.UTF_8));

            String body = "grant_type=refresh_token&refresh_token=" +
                    URLEncoder.encode(safeRefreshToken, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                String responseBody = getResponseBody(response);
                JsonNode json = objectMapper.readTree(responseBody);
                accessToken = json.get("access_token").asText();
                int expiresIn = json.get("expires_in").asInt();
                tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 60) * 1000L; // Refresh 1 min early
                logger.info("Schwab access token refreshed successfully");
                return accessToken;
            } else {
                String errorBody = getResponseBody(response);
                logger.error("Failed to refresh Schwab token. Status: {}. Response: {}", response.statusCode(),
                        errorBody);
                // Force reset access token
                accessToken = null;
                return null;
            }
        } catch (Exception e) {
            logger.error("Error refreshing Schwab token", e);
            accessToken = null;
            return null;
        }
    }

    private String getResponseBody(HttpResponse<InputStream> response) throws java.io.IOException {
        String encoding = response.headers().firstValue("Content-Encoding").orElse("");
        if (encoding.equalsIgnoreCase("gzip")) {
            try (java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(response.body())) {
                return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            return new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Get ATM option quotes for SPX at a specific expiration
     * 
     * @param dte Days to expiration
     * @return Optional containing call and put quotes, or empty if failed
     */
    public Optional<SPXStraddle> getSpxStraddle(int dte) {
        String token = refreshAccessToken();

        if (token == null) {
            logger.error("No valid Schwab access token available after refresh attempt");
            return Optional.empty();
        }

        try {
            // Calculate expiration date
            LocalDate expDate = LocalDate.now().plusDays(dte);
            String expDateStr = expDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

            // Build request URL for SPX option chain
            String url = BASE_URL + "/chains?symbol=$SPX" +
                    "&contractType=ALL" +
                    "&strikeCount=5" + // Get 5 strikes around ATM
                    "&fromDate=" + expDateStr +
                    "&toDate=" + expDateStr;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseOptionChain(response.body());
            } else {
                logger.error("Schwab API error: {} - {}", response.statusCode(), response.body());
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.error("Error fetching SPX options from Schwab", e);
            return Optional.empty();
        }
    }

    /**
     * Parse the option chain response and find ATM straddle
     */
    private Optional<SPXStraddle> parseOptionChain(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            double underlyingPrice = root.path("underlyingPrice").asDouble();

            // Find the closest strike to underlying price
            JsonNode callExpDateMap = root.path("callExpDateMap");
            JsonNode putExpDateMap = root.path("putExpDateMap");

            if (callExpDateMap.isMissingNode() || putExpDateMap.isMissingNode()) {
                logger.warn("No option data found in Schwab response");
                return Optional.empty();
            }

            // Get the first expiration date's strikes
            String firstExpKey = callExpDateMap.fieldNames().next();
            JsonNode callStrikes = callExpDateMap.path(firstExpKey);
            JsonNode putStrikes = putExpDateMap.path(firstExpKey);

            // Find ATM strike (closest to underlying)
            double atmStrike = Math.round(underlyingPrice / 5.0) * 5.0;

            // Get call and put at ATM strike
            JsonNode callOption = findStrike(callStrikes, atmStrike);
            JsonNode putOption = findStrike(putStrikes, atmStrike);

            if (callOption == null || putOption == null) {
                logger.warn("Could not find ATM strike {} in option chain", atmStrike);
                return Optional.empty();
            }

            double callBid = callOption.path("bid").asDouble();
            double callAsk = callOption.path("ask").asDouble();
            double putBid = putOption.path("bid").asDouble();
            double putAsk = putOption.path("ask").asDouble();

            // Parse actual expiration date from the key (format: "2026-01-05:3")
            String actualExpDate = firstExpKey.split(":")[0];

            return Optional.of(new SPXStraddle(
                    callBid, callAsk, putBid, putAsk,
                    underlyingPrice, atmStrike, actualExpDate));

        } catch (Exception e) {
            logger.error("Error parsing Schwab option chain", e);
            return Optional.empty();
        }
    }

    /**
     * Find the option at a specific strike price
     */
    private JsonNode findStrike(JsonNode strikes, double targetStrike) {
        var fields = strikes.fields();
        double minDiff = Double.MAX_VALUE;
        JsonNode closest = null;

        while (fields.hasNext()) {
            var entry = fields.next();
            double strike = Double.parseDouble(entry.getKey());
            double diff = Math.abs(strike - targetStrike);

            if (diff < minDiff) {
                minDiff = diff;
                closest = entry.getValue().get(0); // First contract at this strike
            }
        }
        return closest;
    }

    /**
     * Data class for SPX straddle
     */
    public static class SPXStraddle {
        private final double callBid;
        private final double callAsk;
        private final double putBid;
        private final double putAsk;
        private final double underlyingPrice;
        private final double strike;
        private final String expirationDate;

        public SPXStraddle(double callBid, double callAsk, double putBid, double putAsk,
                double underlyingPrice, double strike, String expirationDate) {
            this.callBid = callBid;
            this.callAsk = callAsk;
            this.putBid = putBid;
            this.putAsk = putAsk;
            this.underlyingPrice = underlyingPrice;
            this.strike = strike;
            this.expirationDate = expirationDate;
        }

        public double getCallMid() {
            return (callBid + callAsk) / 2;
        }

        public double getPutMid() {
            return (putBid + putAsk) / 2;
        }

        public double getStraddlePrice() {
            return getCallMid() + getPutMid();
        }

        public double getUnderlyingPrice() {
            return underlyingPrice;
        }

        public double getStrike() {
            return strike;
        }

        public String getExpirationDate() {
            return expirationDate;
        }
    }
}
