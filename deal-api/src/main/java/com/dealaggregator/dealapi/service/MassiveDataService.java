package com.dealaggregator.dealapi.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.dealaggregator.dealapi.service.YahooOptionsResponse.Contract;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MassiveDataService (Refactored for Yahoo Finance)
 * Fetches real-time option data from Yahoo Finance's unofficial API.
 * Includes explicit session management (Cookie + Crumb) to bypass 401 errors.
 */
@Service
public class MassiveDataService {

    private final RestClient restClient;
    private static final String YAHOO_BASE = "https://query1.finance.yahoo.com/v7/finance/options/";
    private static final String CRUMB_URL = "https://query1.finance.yahoo.com/v1/test/getcrumb";
    private static final String COOKIE_URL = "https://fc.yahoo.com";

    // Session State
    private String currentCookie = null;
    private String currentCrumb = null;

    public MassiveDataService(RestClient.Builder restClientBuilder) {
        // Yahoo requires a User-Agent to avoid 403 Forbidden
        this.restClient = restClientBuilder
                .baseUrl(YAHOO_BASE)
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
     * Initializes or refreshes Yahoo session (Cookie + Crumb).
     * This is required because Yahoo returns 401 if you don't have a valid "crumb".
     */
    private synchronized void ensureSession() {
        if (currentCrumb != null && currentCookie != null)
            return;

        System.out.println("DEBUG: initializing Yahoo Finance Session...");
        try {
            // 1. Fetch Cookie from fc.yahoo.com
            // We use java.net.http.HttpClient here for better cookie control than
            // RestClient
            CookieManager cookieManager = new CookieManager();
            HttpClient client = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(COOKIE_URL))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .GET()
                    .build();

            // This request often 404s or redirects, but sets the cookie in the manager
            client.send(request, HttpResponse.BodyHandlers.discarding());

            // Extract the cookie string
            CookieStore cookieStore = cookieManager.getCookieStore();
            List<HttpCookie> cookies = cookieStore.getCookies();

            if (cookies.isEmpty()) {
                System.err.println("ERROR: Failed to obtain Yahoo Cookie.");
                return;
            }

            // Format cookie string for headers (e.g., "B=...");
            this.currentCookie = cookies.stream()
                    .map(c -> c.getName() + "=" + c.getValue())
                    .collect(Collectors.joining("; "));

            System.out.println("DEBUG: Got Yahoo Cookie: " + (currentCookie.length() > 10 ? "Yes" : "No"));

            // 2. Fetch Crumb using the Cookie
            HttpRequest crumbRequest = HttpRequest.newBuilder()
                    .uri(URI.create(CRUMB_URL))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    // .header("Cookie", this.currentCookie) // HttpClient uses the CookieManager
                    // automatically
                    .GET()
                    .build();

            HttpResponse<String> crumbResponse = client.send(crumbRequest, HttpResponse.BodyHandlers.ofString());

            if (crumbResponse.statusCode() == 200) {
                this.currentCrumb = crumbResponse.body();
                System.out.println("DEBUG: Got Yahoo Crumb: " + currentCrumb);
            } else {
                System.err.println("ERROR: Failed to get Crumb. Status: " + crumbResponse.statusCode());
                // Fallback: Sometimes we don't need a crumb if we have a cookie, or vice-versa,
                // but usually both.
            }

        } catch (Exception e) {
            System.err.println("ERROR: Failed to refresh Yahoo session: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Fetch a specific option contract snapshot using Yahoo Finance.
     */
    public Optional<OptionSnapshot> getOptionSnapshot(String ticker, double strike, String type, int days) {
        ensureSession();

        try {
            // 1. Fetch metadata to get expiration dates
            // We must manually add the Cookie header since RestClient doesn't share the
            // HttpClient's cookie store
            YahooOptionsResponse metadata = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(ticker)
                            .queryParam("crumb", currentCrumb) // Attach crumb if we have it
                            .build())
                    .header("Cookie", currentCookie) // Attach cookie
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
            YahooOptionsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(ticker)
                            .queryParam("date", bestTimestamp)
                            .queryParam("crumb", currentCrumb)
                            .build())
                    .header("Cookie", currentCookie)
                    .retrieve()
                    .body(YahooOptionsResponse.class);

            if (response == null || response.optionChain == null || response.optionChain.result.isEmpty()) {
                return Optional.empty();
            }

            YahooOptionsResponse.OptionData chainData = response.optionChain.result.get(0).options.get(0);
            List<Contract> contracts = type.equalsIgnoreCase("call") ? chainData.calls : chainData.puts;

            if (contracts == null || contracts.isEmpty()) {
                System.out.println("DEBUG: No " + type + " contracts found for this date.");
                return Optional.empty();
            }

            // 4. Find the matching strike
            for (Contract contract : contracts) {
                // Fuzzy match for strike price
                if (Math.abs(contract.strike - strike) < 0.01) {
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
            // If we got a 401, maybe clear the session so next time we retry?
            if (e.getMessage().contains("401")) {
                this.currentCookie = null;
                this.currentCrumb = null;
            }
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
}
