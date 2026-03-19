package com.dealaggregator.dealapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * GEX (Gamma Exposure) Calculator
 *
 * GEX tells us how much dollar-gamma Market Makers need to hedge at each
 * strike.
 * Positive GEX = MMs stabilize the market (buy dips, sell rips).
 * Negative GEX = MMs amplify moves (volatility expands).
 *
 * Formula per strike:
 * Call GEX = gamma * openInterest * 100 * spotPrice^2 * 0.01
 * Put GEX = -gamma * openInterest * 100 * spotPrice^2 * 0.01
 * Net GEX = Call GEX + Put GEX
 */
@Service
public class GexService {

    private static final Logger logger = LoggerFactory.getLogger(GexService.class);

    // -------------------------------------------------------------------------
    // Data Classes — represent one row in the GEX ladder and the full result
    // -------------------------------------------------------------------------

    public static class GexRow {
        public double strike;
        public double callGex;
        public double putGex;
        public double netGex;
        public String label; // e.g. "CALL WALL", "PUT WALL", "ZERO FLIP"

        public GexRow(double strike, double callGex, double putGex) {
            this.strike = strike;
            this.callGex = callGex;
            this.putGex = putGex;
            this.netGex = callGex + putGex;
        }
    }

    public static class GexResult {
        public String symbol;
        public double spotPrice;
        public List<GexRow> rows; // all strikes, sorted highest → lowest
        public double callWall; // strike with highest net GEX (ceiling)
        public double putWall; // strike with lowest net GEX (floor)
        public double zeroFlip; // strike where GEX flips negative (danger)
        public boolean hasZeroFlip;

        public GexResult(String symbol, double spotPrice, List<GexRow> rows,
                double callWall, double putWall,
                double zeroFlip, boolean hasZeroFlip) {
            this.symbol = symbol;
            this.spotPrice = spotPrice;
            this.rows = rows;
            this.callWall = callWall;
            this.putWall = putWall;
            this.zeroFlip = zeroFlip;
            this.hasZeroFlip = hasZeroFlip;
        }
    }

    // -------------------------------------------------------------------------
    // Main method — call this from DiscordBotService
    // -------------------------------------------------------------------------

    /**
     * Calculate GEX from the raw Schwab option chain JSON.
     *
     * The Schwab JSON structure you'll be working with:
     *
     * root
     * ├── underlyingPrice: 512.45
     * ├── callExpDateMap
     * │ └── "2026-03-19:0" (key = "date:dte")
     * │ └── "510.0" (key = strike as a string)
     * │ └── [0] (always an array; grab index 0)
     * │ ├── gamma
     * │ └── openInterest
     * └── putExpDateMap (same shape as callExpDateMap)
     */
    public Optional<GexResult> calculateGex(JsonNode chainRoot, String symbol) {

        // TODO: Step 1 — read the spot price from chainRoot ("underlyingPrice")
        double spotPrice = chainRoot.get("underlyingPrice").asDouble();

        // TODO: Step 2 — loop over callExpDateMap, then putExpDateMap
        // For each expiration key → for each strike key → grab contract at [0]
        // Apply the GEX formula and accumulate into a Map<Double, double[]>
        // where double[0] = callGexSum, double[1] = putGexSum
        TreeMap<Double, double[]> gexMap = new TreeMap<>();
        // --- Call Loop ---
        Iterator<Map.Entry<String, JsonNode>> expirations = chainRoot.get("callExpDateMap").fields();

        while (expirations.hasNext()) {
            JsonNode strikesForThisExpiry = expirations.next().getValue();

            Iterator<Map.Entry<String, JsonNode>> strikes = strikesForThisExpiry.fields();

            while (strikes.hasNext()) {
                Map.Entry<String, JsonNode> strikeEntry = strikes.next();

                double strike = Double.parseDouble(strikeEntry.getKey()); // "510.0" → 510.0
                JsonNode contract = strikeEntry.getValue().get(0); // always an array, grab [0]

                double gamma = contract.path("gamma").asDouble(0);
                double oi = contract.path("openInterest").asDouble(0);
                double callGex = gamma * oi * 100 * spotPrice * spotPrice * 0.01;

                gexMap.computeIfAbsent(strike, k -> new double[] { 0, 0 });
                gexMap.get(strike)[0] += callGex; // [0] = call side
            }
        }

        // --- Put Loop ---
        Iterator<Map.Entry<String, JsonNode>> putExpirations = chainRoot.get("putExpDateMap").fields();

        while (putExpirations.hasNext()) {
            JsonNode strikesForThisExpiry = putExpirations.next().getValue();
            Iterator<Map.Entry<String, JsonNode>> strikes = strikesForThisExpiry.fields();

            while (strikes.hasNext()) {
                Map.Entry<String, JsonNode> strikeEntry = strikes.next();

                double strike = Double.parseDouble(strikeEntry.getKey());
                JsonNode contract = strikeEntry.getValue().get(0);

                double gamma = contract.path("gamma").asDouble(0);
                double oi = contract.path("openInterest").asDouble(0);
                double putGex = -gamma * oi * 100 * spotPrice * spotPrice * 0.01; // negative sign!

                gexMap.computeIfAbsent(strike, k -> new double[] { 0, 0 });
                gexMap.get(strike)[1] += putGex; // [1] = put side
            }
        }

        // TODO: Step 3 — convert the map into a List <GexRow> sorted high → low strike
        List<GexRow> rows = new ArrayList<>();

        for (Map.Entry<Double, double[]> entry : gexMap.descendingMap().entrySet()) {
            double strike = entry.getKey();
            double callGex = entry.getValue()[0];
            double putGex = entry.getValue()[1];
            rows.add(new GexRow(strike, callGex, putGex));
        }

        // TODO: Step 4 — find the three milestones:
        // callWall = row with highest netGex
        // putWall = row with lowest netGex
        // zeroFlip = first row BELOW spot where netGex flips negative

        // CALL WALL
        GexRow callWallRow = rows.get(0);
        for (GexRow row : rows) {
            if (row.netGex > callWallRow.netGex) {
                callWallRow = row;
            }
        }

        // PUT WALL
        GexRow putWallRow = rows.get(0);
        for (GexRow row : rows) {
            if (row.netGex < putWallRow.netGex) {
                putWallRow = row;
            }
        }

        // ZERO FLIP
        GexRow zeroFlipRow = null; // null means "not found yet"
        for (GexRow row : rows) {
            if (row.netGex < 0) {
                zeroFlipRow = row;
                break;
            }
        }

        // Then update the label + return accordingly:
        if (zeroFlipRow != null)
            zeroFlipRow.label = "⚡ ZERO FLIP";

        return Optional.of(new GexResult(
                symbol, spotPrice, rows,
                callWallRow.strike,
                putWallRow.strike,
                zeroFlipRow != null ? zeroFlipRow.strike : Double.NaN,
                zeroFlipRow != null // false if no flip found
        ));
    }
}
