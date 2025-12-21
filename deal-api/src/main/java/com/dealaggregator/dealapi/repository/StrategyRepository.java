package com.dealaggregator.dealapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import com.dealaggregator.dealapi.entity.Strategy;
import com.dealaggregator.dealapi.entity.StrategyStatus;

public interface StrategyRepository extends JpaRepository<Strategy, Long> {
    List<Strategy> findByUserId(String userId);

    List<Strategy> findByUserIdAndStatus(String userId, StrategyStatus status);
}
