package com.dealaggregator.dealapi.service;

import java.util.List;
import org.springframework.stereotype.Service;
import com.dealaggregator.dealapi.entity.Strategy;
import com.dealaggregator.dealapi.entity.Leg;
import com.dealaggregator.dealapi.repository.StrategyRepository;

@Service
public class StrategyService {

    private final StrategyRepository strategyRepo;

    // Constructor injection
    public StrategyService(StrategyRepository strategyRepo) {
        this.strategyRepo = strategyRepo;
    }

    /**
     * Create and save a new strategy with its legs.
     */
    public Strategy openStrategy(String userId, String strategyType, String ticker, List<Leg> legs) {

        Strategy strategy = new Strategy(userId, strategyType, ticker);
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

        return strategyRepo.findByUserIdAndStatus(userId, "OPEN");
    }

    /**
     * Close a strategy by ID.
     */
    public void closeStrategy(Long strategyId) {

        Strategy strategy = strategyRepo.findById(strategyId).get();
        strategy.setStatus("CLOSED");
        strategyRepo.save(strategy);
    }
}