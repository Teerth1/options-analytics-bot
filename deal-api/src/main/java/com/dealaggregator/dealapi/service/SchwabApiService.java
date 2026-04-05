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
import org.springframework.beans.factory.annotation.Autowired;
import com.dealaggregator.dealapi.model.SchwabToken;

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
    private static final String TOKEN_ID = "default";
    private static final String TOKENS_FILE = "schwab_tokens.json";

    @Autowired
    private com.dealaggregator.dealapi.repository.SchwabTokenRepository tokenRepository;
    @Value("${schwab.client.id}")
    private String clientId;

    @Value("${schwab.client.secret:}")
    private String clientSecret;

    @Value("${schwab.refresh.token:}")
    private String refreshToken;

    @Value("${app.base.url:https://deal-aggregator-production.up.railway.app}")
    private String appBaseUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String accessToken;
    private long tokenExpiresAt = 0;

    public SchwabApiService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        logger.info("SchwabApiService initialized. Attempting to bootstrap tokens...");
    }

    /**
     * Force a token refresh regardless of expiry status.
     * Used by scheduled keepalive jobs to prevent token expiration.
     * 
     * @return true if refresh succeeded, false otherwise
     */
    public boolean forceTokenRefresh() {
        logger.info("Forcing token refresh (keepalive)...");
        // Reset expiry to force a refresh
        this.tokenExpiresAt = 0;
        String token = refreshAccessToken();
        return token != null;
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
    // Duplicate private static final String TOKENS_FILE removed here

    /**
     * Refresh the access token using the separate refresh token.
     * This ensures we always have a valid session to talk to Schwab.
     * 
     * Logic:
     * 1. Try to load persisted tokens from disk first (bootstrap/recovery).
     * 2. Check if current token is expired.
     * 3. If yes, POST to Schwab's OAuth endpoint.
     * 4. Update the local access token AND refresh token (rotation).
     * 5. Persist new tokens to disk for future restarts.
     */
    private synchronized String refreshAccessToken() {
        // Try loading from file first if we haven't yet, or if we are about to fail
        if (accessToken == null) {
            loadPersistedTokens();
        }

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

                // Capture the new refresh token if Schwab rotated it
                if (json.has("refresh_token")) {
                    String newRefreshToken = json.get("refresh_token").asText();
                    if (!newRefreshToken.isEmpty() && !newRefreshToken.equals(refreshToken)) {
                        logger.info("Received new refresh token from Schwab. Rotating...");
                        this.refreshToken = newRefreshToken;
                    }
                }

                // Always persist after a successful refresh so the DB stays current.
                // Previously this only ran on refresh token rotation — meaning the DB
                // would go stale between rotations and never reflect the latest access token.
                persistTokens();

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

    /**
     * Generates the Schwab authorization URL for the user to log in.
     */
    public String getAuthorizationUrl() {
        String redirectUri = appBaseUrl + "/auth/schwab/callback";
        return "https://api.schwabapi.com/v1/oauth/authorize?" +
                "client_id=" + clientId.trim() +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
    }

    /**
     * Exchanges the authorization code for initial tokens.
     * This is the "7-day" manual re-auth flow made automatic.
     */
    public boolean exchangeCodeForTokens(String code) {
        try {
            String safeClientId = clientId.trim();
            String safeClientSecret = clientSecret.trim();
            String redirectUri = appBaseUrl + "/auth/schwab/callback";

            String auth = Base64.getEncoder().encodeToString(
                    (safeClientId + ":" + safeClientSecret).getBytes(StandardCharsets.UTF_8));

            String body = "grant_type=authorization_code" +
                    "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                    "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

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

                this.accessToken = json.get("access_token").asText();
                this.refreshToken = json.get("refresh_token").asText();
                int expiresIn = json.get("expires_in").asInt();
                this.tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 60) * 1000L;

                persistTokens();
                logger.info("✅ Full Schwab re-authorization successful!");
                return true;
            } else {
                String errorBody = getResponseBody(response);
                logger.error("Failed to exchange Schwab code. Status: {}. Response: {}", response.statusCode(), errorBody);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error exchanging Schwab code", e);
            return false;
        }
    }

    private void loadPersistedTokens() {
        try {
            // Capture the token injected from Environment/Properties BEFORE we overwrite it
            String envRefreshToken = this.refreshToken;
            boolean validEnvToken = envRefreshToken != null && !envRefreshToken.isEmpty()
                    && !envRefreshToken.equals("${SCHWAB_REFRESH_TOKEN}");

            // 1. DATABASE IS THE SOURCE OF TRUTH.
            // If the DB has a valid token, always use it — even if the env var differs.
            // The env var becomes stale after the first token rotation, so trusting it
            // over the DB would clobber the freshly-rotated token and break auth.
            Optional<SchwabToken> dbTokenOpt = tokenRepository.findById(TOKEN_ID);
            if (dbTokenOpt.isPresent()) {
                SchwabToken dbToken = dbTokenOpt.get();
                String dbRefreshToken = dbToken.getRefreshToken();
                String dbAccessToken = dbToken.getAccessToken();

                if (dbRefreshToken != null && !dbRefreshToken.isEmpty()) {
                    this.refreshToken = dbRefreshToken;
                    this.accessToken = null;       // always get a fresh access token
                    this.tokenExpiresAt = 0;       // force a real exchange on next call
                    logger.info("✅ Loaded Schwab tokens from DATABASE. Refresh Token: {}...",
                            (this.refreshToken.length() > 10 ? this.refreshToken.substring(0, 10) : "short"));
                    return; // DB wins — done.
                }
            }

            // 2. DB is empty — fall back to env var (bootstrap / first-time setup).
            // This is the ONLY time the env var is used. Once the DB has a token,
            // the env var is ignored so stale Railway variables don't break rotations.
            if (validEnvToken) {
                logger.info("⚠️  DB empty. Bootstrapping from ENVIRONMENT token.");
                this.refreshToken = envRefreshToken;
                this.accessToken = null; // Force fresh access token
                persistTokens(); // Save to DB so we never need the env var again
                return;
            }

            // 3. Last Resort: Check legacy local file (schwab_tokens.json)
            java.io.File file = new java.io.File(TOKENS_FILE);
            if (file.exists()) {
                try (InputStream tokenStream = new java.io.FileInputStream(file)) {
                    JsonNode root = objectMapper.readTree(tokenStream);
                    if (root.has("refresh_token")) {
                        this.refreshToken = root.get("refresh_token").asText();
                        this.accessToken = null;
                        logger.info("⚠️  Loaded Schwab REFRESH token from LOCAL FILE: {}", TOKENS_FILE);
                        persistTokens(); // Migrate to DB
                        return;
                    }
                }
            }

            logger.error(
                    "❌ CRITICAL: No valid Schwab Refresh Token found in Database, Environment, or File! Bot will fail to authenticate.");

        } catch (Exception e) {
            logger.error("Failed to load persisted tokens", e);
        }
    }

    private void persistTokens() {
        try {
            // Save to Database
            SchwabToken token = new SchwabToken(TOKEN_ID, this.refreshToken, this.accessToken,
                    System.currentTimeMillis());
            tokenRepository.save(token);
            logger.info("💾 Persisted Schwab tokens to Database. Refresh Token: {}...",
                    (this.refreshToken != null && this.refreshToken.length() > 10) ? this.refreshToken.substring(0, 10)
                            : "null");
        } catch (Exception e) {
            logger.error("❌ Failed to persist Schwab tokens to Database", e);
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

            // Extract implied volatility from each contract
            double callIV = callOption.path("volatility").asDouble();
            double putIV = putOption.path("volatility").asDouble();

            // Parse actual expiration date from the key (format: "2026-01-05:3")
            String actualExpDate = firstExpKey.split(":")[0];

            return Optional.of(new SPXStraddle(
                    callBid, callAsk, putBid, putAsk,
                    underlyingPrice, atmStrike, actualExpDate,
                    callIV, putIV));

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
     * Normalize a user-supplied ticker to the symbol Schwab expects in the API.
     * SPX (S&P 500 cash index) must be prefixed with "$" in Schwab's system.
     * SPXW (weekdaily SPX) is also mapped. All other tickers pass through unchanged.
     */
    private static String toSchwabSymbol(String ticker) {
        String upper = ticker.trim().toUpperCase();
        if (upper.equals("SPX") || upper.equals("SPXW")) {
            return "$" + upper;
        }
        return upper;
    }

    /**
     * Fetch the full, broad option chain for any ticker.
     *
     * This is the core data-harvest call for GEX analysis. It pulls 50 strikes
     * on each side of ATM across ALL expirations so that the GexService can
     * compute cumulative gamma from 0DTE all the way out to monthlies.
     *
     * Schwab JSON shape returned (abbreviated):
     * <pre>
     * {
     *   "underlyingPrice": 512.45,
     *   "callExpDateMap": {
     *     "2026-03-19:0": {               // key = "date:dte"
     *       "510.0": [ {                  // key = strike as string
     *         "gamma": 0.0142,
     *         "openInterest": 12500,
     *         ...many other fields...
     *       } ]
     *     }
     *   },
     *   "putExpDateMap": { (same shape as callExpDateMap) }
     * }
     * </pre>
     *
     * @param ticker e.g. "SPY", "SPX", "QQQ" — SPX is automatically normalized to "$SPX"
     * @return Optional containing the root JsonNode, or empty if the request failed.
     */
    public java.util.Optional<JsonNode> getFullOptionChain(String ticker) {
        String token = refreshAccessToken();
        if (token == null) {
            logger.error("No valid Schwab token available for getFullOptionChain");
            return java.util.Optional.empty();
        }

        String symbol = toSchwabSymbol(ticker);
        try {
            String encodedSymbol = java.net.URLEncoder.encode(symbol, StandardCharsets.UTF_8);
            String url = BASE_URL + "/chains"
                    + "?symbol=" + encodedSymbol
                    + "&contractType=ALL"
                    + "&strikeCount=50"          // 50 strikes each side of ATM
                    + "&includeUnderlyingQuote=true"; // embeds spot price in response

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                String body = getResponseBody(response);
                JsonNode root = objectMapper.readTree(body);
                logger.info("Fetched full option chain for {} ({} status)", symbol, response.statusCode());
                return java.util.Optional.of(root);
            } else {
                String errorBody = getResponseBody(response);
                logger.error("Schwab option chain error for {}: {} — {}", symbol, response.statusCode(), errorBody);
                return java.util.Optional.empty();
            }
        } catch (Exception e) {
            logger.error("Error fetching option chain for {}", symbol, e);
            return java.util.Optional.empty();
        }
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
        private final double callIV;
        private final double putIV;

        public SPXStraddle(double callBid, double callAsk, double putBid, double putAsk,
                double underlyingPrice, double strike, String expirationDate,
                double callIV, double putIV) {
            this.callBid = callBid;
            this.callAsk = callAsk;
            this.putBid = putBid;
            this.putAsk = putAsk;
            this.underlyingPrice = underlyingPrice;
            this.strike = strike;
            this.expirationDate = expirationDate;
            this.callIV = callIV;
            this.putIV = putIV;
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

        public double getCallIV() {
            return callIV;
        }

        public double getPutIV() {
            return putIV;
        }

        public double getAverageIV() {
            return (callIV + putIV) / 2.0;
        }
    }
}
