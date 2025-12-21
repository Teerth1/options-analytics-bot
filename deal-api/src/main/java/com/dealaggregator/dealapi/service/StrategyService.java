package com.dealaggregator.dealapi.service;

import java.util.List;
import org.springframework.stereotype.Service;
import com.dealaggregator.dealapi.entity.Strategy;
import com.dealaggregator.dealapi.entity.StrategyStatus;
import com.dealaggregator.dealapi.entity.Leg;
import com.dealaggregator.dealapi.repository.StrategyRepository;

/**
 * Service layer for managing options trading strategies.
 * 
 * Handles the business logic for:
 * - Opening new strategies (single-leg or multi-leg spreads)
 * - Retrieving user portfolios
 * - Closing/selling strategies
 * 
 * A Strategy can contain multiple Legs (e.g., vertical spread has 2 legs,
 * iron condor has 4 legs). Each Leg represents one options contract.
 * 
 * @see Strategy
 * @see Leg
 */
@Service
public class StrategyService {

    private final StrategyRepository strategyRepo;

    /**
     * Constructor with dependency injection.
     * Spring automatically injects the StrategyRepository.
     */
    public StrategyService(StrategyRepository strategyRepo) {
        this.strategyRepo = strategyRepo;
    }

    /**
     * Create and save a new strategy with its legs.
     */
    public Strategy openStrategy(String userId, String strategyType, String ticker, List<Leg> legs) {
        return openStrategy(userId, strategyType, ticker, legs, null);
    }

    /**
     * Create and save a new strategy with its legs and net cost.
     * Use this for spreads where net debit/credit matters.
     */
    public Strategy openStrategy(String userId, String strategyType, String ticker, List<Leg> legs, Double netCost) {
        Strategy strategy = new Strategy(userId, strategyType, ticker, netCost);
        for (Leg leg : legs) {
            leg.setStrategy(strategy);
        }
        strategy.getLegs().addAll(legs);
        return strategyRepo.save(strategy);
    }

    /**
     * Get all open strategies for a user.
     */
    public List<Strategy> getOpenStrategies(String userId) {

        return strategyRepo.findByUserIdAndStatus(userId, StrategyStatus.OPEN);
    }

    /**
     * Close a strategy by ID.
     */
    public void closeStrategy(Long strategyId) {

        Strategy strategy = strategyRepo.findById(strategyId).get();
        strategy.setStatus(StrategyStatus.CLOSED);
        strategyRepo.save(strategy);
    }
}