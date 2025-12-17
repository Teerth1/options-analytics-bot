package com.dealaggregator.dealapi.dto;

import java.util.List;

/**
 * Represents the top-level response from Massive.com API
 * 
 * Example JSON:
 * {
 * "status": "OK",
 * "results": [...],
 * "next_url": "https://api.massive.com/v3/snapshot/options/SPX?cursor=abc123"
 * }
 */
public record MassiveApiResponse(
        String status,
        List<OptionContract> results,
        String next_url // Used for pagination
) {
}