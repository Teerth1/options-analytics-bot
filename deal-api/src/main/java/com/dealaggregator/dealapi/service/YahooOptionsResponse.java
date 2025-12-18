package com.dealaggregator.dealapi.service;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO to map Yahoo Finance Options API response.
 * Structure: optionChain -> result[] -> options[] -> calls[] / puts[]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class YahooOptionsResponse {

    public OptionChain optionChain;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OptionChain {
        public List<Result> result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        public Quote quote;
        public List<Long> expirationDates;
        public List<OptionData> options;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Quote {
        public String language;
        public String region;
        public String quoteType;
        public String currency;
        public double regularMarketPrice;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OptionData {
        public long expirationDate;
        public List<Contract> calls;
        public List<Contract> puts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Contract {
        public double strike;
        public double lastPrice;
        public double bid;
        public double ask;
        public int volume;
        public int openInterest;
        public double impliedVolatility;
        public boolean inTheMoney;
        public String contractSymbol;
    }
}
