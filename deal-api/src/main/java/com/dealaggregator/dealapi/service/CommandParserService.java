package com.dealaggregator.dealapi.service;

import org.springframework.stereotype.Service;

@Service
public class CommandParserService {

    public static class ParsedOption {
        public String ticker;
        public double strike;
        public String type; // "call" or "put"
        public int days;
    }

    /**
     * Parses a string like "NVDA 150c 30d" or "AAPL 200p 45"
     */
    public ParsedOption parse(String query) {
        ParsedOption result = new ParsedOption();
        String[] parts = query.toUpperCase().split(" ");
        
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid command format. Expected: <TICKER> <STRIKE><c/p> <DAYS>d");
        }
        result.ticker = parts[0];
        // 2. Strike & Type (e.g., "150C" or "150")
        String strikePart = parts[1];
        if (strikePart.endsWith("C")) {
            result.type = "call";
            result.strike = Double.parseDouble(strikePart.substring(0, strikePart.length() - 1));
        } else if (strikePart.endsWith("P")) {
            result.type = "put";
            result.strike = Double.parseDouble(strikePart.substring(0, strikePart.length() - 1));
        } else {
            result.type = "call"; // Default to call if not specified
            result.strike = Double.parseDouble(strikePart);
        }
        // 3. Days (e.g., "30d" or "30")
        String daysPart = parts[2].replace("D", "");
        result.days = Integer.parseInt(daysPart);
        return result;
    }

}