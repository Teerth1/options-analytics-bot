package com.dealaggregator.dealapi.service;

/**
 * Represents a single option contract from the API
 * 
 * Example:
 * {
 * "details": {
 * "contract_type": "call",
 * "strike_price": 5000.0,
 * "expiration_date": "2025-01-17"
 * },
 * "last_quote": {
 * "bid": 10.5,
 * "ask": 11.0
 * },
 * "day": {
 * "volume": 1234,
 * "open_interest": 5678
 * }
 * }
 */
public record OptionContract(
        Details details,
        LastQuote last_quote,
        DayData day) {
}

// Nested records for structure
record Details(
        String contract_type, // "call" or "put"
        Double strike_price,
        String expiration_date) {
}

record LastQuote(
        Double bid,
        Double ask) {
}

record DayData(
        Integer volume,
        Integer open_interest) {
}