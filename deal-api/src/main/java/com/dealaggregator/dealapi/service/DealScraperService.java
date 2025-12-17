package com.dealaggregator.dealapi.service;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.dealaggregator.dealapi.repository.DealRepository;

import com.dealaggregator.dealapi.entity.Deal;

/**
 * Service class for web scraping deals from online sources.
 *
 * This service uses Jsoup to scrape deal information from Reddit's
 * r/buildapcsales subreddit. It extracts deal details, parses pricing
 * information, identifies vendors, and saves deals to the database.
 *
 * The scraping runs automatically on a scheduled basis using Spring's
 * 
 * @Scheduled annotation.
 */
@Service
public class DealScraperService {

    private final DealRepository dealRepo;

    /**
     * Constructor for DealScraperService with dependency injection.
     *
     * @param dealRepo Repository for database operations on Deal entities
     */
    public DealScraperService(DealRepository dealRepo) {
        this.dealRepo = dealRepo;
    }

    /**
     * Test method to verify Jsoup is working correctly.
     */
    public void testJsoup() {
        System.out.println("Jsoup chilling");
    }

    /**
     * Extracts vendor name from a deal URL.
     *
     * Analyzes the URL to identify the retailer/vendor where the deal
     * is available. Supports major online retailers including Amazon,
     * Newegg, Best Buy, Walmart, Target, Micro Center, and B&H Photo.
     *
     * @param url Deal URL to analyze
     * @return Vendor name (e.g., "Amazon", "Newegg") or "Other" if unknown
     */
    private String extractVendor(String url) {
        if (url.contains("amazon.com")) {
            return "Amazon";
        } else if (url.contains("newegg.com")) {
            return "Newegg";
        } else if (url.contains("bestbuy.com")) {
            return "Best Buy";
        } else if (url.contains("microcenter.com")) {
            return "Micro Center";
        } else if (url.contains("walmart.com")) {
            return "Walmart";
        } else if (url.contains("target.com")) {
            return "Target";
        } else if (url.contains("bhphotovideo.com")) {
            return "B&H Photo";
        } else {
            return "Other";
        }
    }

    /**
     * Extracts price information from a deal title.
     *
     * Uses regex pattern matching to find dollar amounts in the format
     * $XX.XX or $X,XXX.XX within the deal title. Handles comma separators
     * for large amounts.
     *
     * @param title Deal title containing price information
     * @return BigDecimal representing the extracted price, or BigDecimal.ZERO if no
     *         price found
     */
    private BigDecimal extractPrice(String title) {
        try {
            // Regex pattern to match prices like $99.99 or $1,299.99
            Pattern pattern = Pattern.compile("\\$([0-9,]+\\.?[0-9]*)");
            Matcher matcher = pattern.matcher(title);
            if (matcher.find()) {
                // Remove commas and convert to BigDecimal
                String priceStr = matcher.group(1).replace(",", "");
                return new BigDecimal(priceStr);
            }
        } catch (Exception e) {
            System.out.println("Could not extract price from: " + title);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Extracts product category from a deal title.
     *
     * Reddit r/buildapcsales posts typically start with category tags in
     * brackets like [GPU], [CPU], [Monitor]. This method extracts the
     * category from within the brackets.
     *
     * @param title Deal title containing category tag
     * @return Category name (e.g., "GPU", "CPU") or "Other" if no category tag
     *         found
     */
    private String extractCategory(String title) {
        if (title.startsWith("[")) {
            int endBracket = title.indexOf("]");
            if (endBracket > 0) {
                return title.substring(1, endBracket);
            }
        }
        return "Other";
    }

    /**
     * Scheduled method to scrape deals from Reddit's r/buildapcsales subreddit.
     *
     * This method runs automatically every 60 seconds (60000ms) as specified by
     * the @Scheduled annotation. It connects to the old Reddit interface, parses
     * the HTML structure, extracts deal information, and saves each deal to the
     * database.
     *
     * The scraper extracts:
     * - Deal title
     * - Deal URL (link to the product)
     * - Vendor (extracted from URL)
     * - Category (from title tag)
     * - Price (parsed from title)
     *
     * All scraped deals are automatically saved to the database with deal type
     * "Online".
     */
    @Scheduled(fixedRate = 60000)
    public void scrapeReddit() {
        try {
            // Connect to Reddit with a user agent (required for scraping)
            Document doc = Jsoup.connect("https://old.reddit.com/r/buildapcsales")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)").get();

            // Select all post elements
            Elements posts = doc.select("div.thing");

            // Process each post
            for (Element post : posts) {
                String title = post.select("a.title").text();
                String link = post.select("a.title").attr("href");
                String score = post.attr("data-score");

                // Create and populate Deal object
                Deal deal = new Deal();
                deal.setTitle(title);
                deal.setDealUrl(link);
                deal.setVendor(extractVendor(link));
                deal.setCategory(extractCategory(title));
                deal.setDealType("Online");
                deal.setPrice(extractPrice(title));

                // Save deal to database
                dealRepo.save(deal);
            }

        } catch (Exception e) {
            System.out.print("Error: " + e.getMessage());
        }
    }
}
