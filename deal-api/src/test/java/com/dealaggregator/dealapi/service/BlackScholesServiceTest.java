package com.dealaggregator.dealapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BlackScholesService.
 * 
 * Tests option pricing calculations for both call and put options
 * using known values to verify accuracy.
 */
class BlackScholesServiceTest {

    private BlackScholesService blackScholesService;

    @BeforeEach
    void setUp() {
        blackScholesService = new BlackScholesService();
    }

    // ==================== CALL OPTION TESTS ====================

    @Test
    @DisplayName("Call option - at-the-money should have significant value")
    void testCallOption_AtTheMoney() {
        // Stock at $100, Strike at $100 (ATM)
        double stockPrice = 100.0;
        double strike = 100.0;
        double timeToExpiry = 30.0 / 365.0; // 30 days
        double volatility = 0.20; // 20% annual
        double riskFreeRate = 0.05; // 5%

        double price = blackScholesService.blackScholes(
                stockPrice, strike, timeToExpiry, volatility, riskFreeRate, "call");

        // ATM call should have positive value
        assertTrue(price > 0, "ATM call should have positive value");
        assertTrue(price < stockPrice * 0.20, "Call shouldn't exceed 20% of stock price for 30 days");
    }

    @Test
    @DisplayName("Call option - deep in-the-money approaches intrinsic value")
    void testCallOption_DeepInTheMoney() {
        double stockPrice = 150.0;
        double strike = 100.0; // Deep ITM
        double timeToExpiry = 30.0 / 365.0;
        double volatility = 0.20;
        double riskFreeRate = 0.05;

        double price = blackScholesService.blackScholes(
                stockPrice, strike, timeToExpiry, volatility, riskFreeRate, "call");

        double intrinsicValue = stockPrice - strike; // $50

        // Deep ITM call should be close to intrinsic value
        assertTrue(price >= intrinsicValue * 0.95,
                "Deep ITM call should be at least 95% of intrinsic value");
    }

    @Test
    @DisplayName("Call option - out-of-the-money should have small value")
    void testCallOption_OutOfTheMoney() {
        double stockPrice = 100.0;
        double strike = 150.0; // OTM
        double timeToExpiry = 30.0 / 365.0;
        double volatility = 0.20;
        double riskFreeRate = 0.05;

        double price = blackScholesService.blackScholes(
                stockPrice, strike, timeToExpiry, volatility, riskFreeRate, "call");

        // OTM call should have small but positive value
        assertTrue(price >= 0, "OTM call should not be negative");
        assertTrue(price < 5, "OTM call should be very small");
    }

    // ==================== PUT OPTION TESTS ====================

    @Test
    @DisplayName("Put option - at-the-money should have significant value")
    void testPutOption_AtTheMoney() {
        double stockPrice = 100.0;
        double strike = 100.0;
        double timeToExpiry = 30.0 / 365.0;
        double volatility = 0.20;
        double riskFreeRate = 0.05;

        double price = blackScholesService.blackScholes(
                stockPrice, strike, timeToExpiry, volatility, riskFreeRate, "put");

        assertTrue(price > 0, "ATM put should have positive value");
    }

    @Test
    @DisplayName("Put option - deep in-the-money approaches intrinsic value")
    void testPutOption_DeepInTheMoney() {
        double stockPrice = 50.0;
        double strike = 100.0; // Deep ITM for put
        double timeToExpiry = 30.0 / 365.0;
        double volatility = 0.20;
        double riskFreeRate = 0.05;

        double price = blackScholesService.blackScholes(
                stockPrice, strike, timeToExpiry, volatility, riskFreeRate, "put");

        double intrinsicValue = strike - stockPrice; // $50

        assertTrue(price >= intrinsicValue * 0.90,
                "Deep ITM put should be close to intrinsic value");
    }

    // ==================== PUT-CALL PARITY TESTS ====================

    @Test
    @DisplayName("Put-Call Parity relationship holds")
    void testPutCallParity() {
        // Put-Call Parity: C - P = S - K*e^(-rt)
        double stockPrice = 100.0;
        double strike = 100.0;
        double timeToExpiry = 90.0 / 365.0; // 90 days
        double volatility = 0.25;
        double riskFreeRate = 0.05;

        double callPrice = blackScholesService.blackScholes(
                stockPrice, strike, timeToExpiry, volatility, riskFreeRate, "call");
        double putPrice = blackScholesService.blackScholes(
                stockPrice, strike, timeToExpiry, volatility, riskFreeRate, "put");

        double leftSide = callPrice - putPrice;
        double rightSide = stockPrice - strike * Math.exp(-riskFreeRate * timeToExpiry);

        // Allow small tolerance due to rounding
        assertEquals(rightSide, leftSide, 0.05,
                "Put-Call Parity should hold within tolerance");
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    @DisplayName("Higher volatility increases option value")
    void testHigherVolatilityIncreasesValue() {
        double stockPrice = 100.0;
        double strike = 100.0;
        double timeToExpiry = 30.0 / 365.0;
        double riskFreeRate = 0.05;

        double priceLowVol = blackScholesService.blackScholes(
                stockPrice, strike, timeToExpiry, 0.15, riskFreeRate, "call");
        double priceHighVol = blackScholesService.blackScholes(
                stockPrice, strike, timeToExpiry, 0.35, riskFreeRate, "call");

        assertTrue(priceHighVol > priceLowVol,
                "Higher volatility should increase option value");
    }

    @Test
    @DisplayName("Longer time to expiry increases option value")
    void testLongerTimeIncreasesValue() {
        double stockPrice = 100.0;
        double strike = 100.0;
        double volatility = 0.20;
        double riskFreeRate = 0.05;

        double priceShortTerm = blackScholesService.blackScholes(
                stockPrice, strike, 7.0 / 365.0, volatility, riskFreeRate, "call");
        double priceLongTerm = blackScholesService.blackScholes(
                stockPrice, strike, 90.0 / 365.0, volatility, riskFreeRate, "call");

        assertTrue(priceLongTerm > priceShortTerm,
                "Longer time to expiry should increase option value");
    }
}
