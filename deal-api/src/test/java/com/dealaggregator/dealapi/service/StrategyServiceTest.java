package com.dealaggregator.dealapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dealaggregator.dealapi.entity.Leg;
import com.dealaggregator.dealapi.entity.Strategy;
import com.dealaggregator.dealapi.entity.StrategyStatus;
import com.dealaggregator.dealapi.repository.StrategyRepository;

/**
 * Unit tests for StrategyService.
 * Uses Mockito to mock the repository layer.
 */
@ExtendWith(MockitoExtension.class)
class StrategyServiceTest {

    @Mock
    private StrategyRepository strategyRepo;

    private StrategyService strategyService;

    @BeforeEach
    void setUp() {
        strategyService = new StrategyService(strategyRepo);
    }

    // ==================== OPEN STRATEGY TESTS ====================

    @Test
    @DisplayName("Open strategy - creates strategy with legs")
    void testOpenStrategy_CreatesWithLegs() {
        // Arrange
        String userId = "user123";
        String strategyType = "CALL";
        String ticker = "AAPL";

        // Use the actual constructor
        Leg leg1 = new Leg("call", 150.0, LocalDate.now().plusDays(30), 5.0, 1);
        List<Leg> legs = Arrays.asList(leg1);

        when(strategyRepo.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Strategy result = strategyService.openStrategy(userId, strategyType, ticker, legs);

        // Assert
        assertEquals(userId, result.getUserId());
        assertEquals(strategyType, result.getStrategy()); // Field is 'strategy' not 'strategyType'
        assertEquals(ticker, result.getTicker());
        assertEquals(1, result.getLegs().size());
        verify(strategyRepo, times(1)).save(any(Strategy.class));
    }

    @Test
    @DisplayName("Open strategy - sets leg-strategy relationship")
    void testOpenStrategy_SetsLegStrategyRelationship() {
        // Arrange
        Leg leg1 = new Leg();
        Leg leg2 = new Leg();
        List<Leg> legs = Arrays.asList(leg1, leg2);

        when(strategyRepo.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Strategy result = strategyService.openStrategy("user", "SPREAD", "SPY", legs);

        // Assert - each leg should reference the parent strategy
        for (Leg leg : result.getLegs()) {
            assertEquals(result, leg.getStrategy());
        }
    }

    @Test
    @DisplayName("Open strategy with net cost - stores debit/credit amount")
    void testOpenStrategy_WithNetCost() {
        // Arrange
        Double netCost = -2.50; // Credit spread
        Leg leg = new Leg();

        when(strategyRepo.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Strategy result = strategyService.openStrategy("user", "CREDIT_SPREAD", "QQQ",
                Arrays.asList(leg), netCost);

        // Assert
        assertEquals(netCost, result.getNetCost());
    }

    // ==================== GET STRATEGIES TESTS ====================

    @Test
    @DisplayName("Get open strategies - returns only OPEN status")
    void testGetOpenStrategies_ReturnsOnlyOpen() {
        // Arrange
        Strategy strategy1 = new Strategy("user1", "CALL", "AAPL");
        Strategy strategy2 = new Strategy("user1", "PUT", "MSFT");
        List<Strategy> expectedStrategies = Arrays.asList(strategy1, strategy2);

        when(strategyRepo.findByUserIdAndStatus("user1", StrategyStatus.OPEN))
                .thenReturn(expectedStrategies);

        // Act
        List<Strategy> result = strategyService.getOpenStrategies("user1");

        // Assert
        assertEquals(2, result.size());
        verify(strategyRepo).findByUserIdAndStatus("user1", StrategyStatus.OPEN);
    }

    @Test
    @DisplayName("Get open strategies - empty list for new user")
    void testGetOpenStrategies_EmptyForNewUser() {
        // Arrange
        when(strategyRepo.findByUserIdAndStatus("newuser", StrategyStatus.OPEN))
                .thenReturn(Arrays.asList());

        // Act
        List<Strategy> result = strategyService.getOpenStrategies("newuser");

        // Assert
        assertTrue(result.isEmpty());
    }

    // ==================== CLOSE STRATEGY TESTS ====================

    @Test
    @DisplayName("Close strategy - changes status to CLOSED")
    void testCloseStrategy_ChangesStatus() {
        // Arrange
        Long strategyId = 1L;
        Strategy strategy = new Strategy("user", "CALL", "AAPL");
        strategy.setStatus(StrategyStatus.OPEN);

        when(strategyRepo.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(strategyRepo.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        strategyService.closeStrategy(strategyId);

        // Assert
        assertEquals(StrategyStatus.CLOSED, strategy.getStatus());
        verify(strategyRepo).save(strategy);
    }
}
