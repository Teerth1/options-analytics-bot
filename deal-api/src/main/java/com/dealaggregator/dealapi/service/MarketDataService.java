package com.dealaggregator.dealapi.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;


@Service
public class MarketDataService {

    /**
     * Scrapes the real-time price of a stock ticker.
     */
    public double getPrice(String ticker) {
        try {
            // URL for CNBC Quote Page (e.g. https://www.cnbc.com/quotes/NVDA)
            String url = "https://www.cnbc.com/quotes/" + ticker.toUpperCase();
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(5000) // Don't wait more than 5 seconds
                    .get();
            // Parse the price from the HTML
            String priceText = doc.select(".QuoteStrip-lastPrice").text();
            
            // 2. Clean the text (Remove "$" symbols and "," separators)
            // Example: "$1,450.50" -> "1450.50"
            priceText = priceText.replace("$", "").replace(",", "");
            return Double.parseDouble(priceText);
            
            
        } catch (Exception e) {
            System.out.println("⚠️ Could not fetch price for " + ticker + ": " + e.getMessage());
            return 0.0;
        }
        
    }

}