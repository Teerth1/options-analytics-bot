package com.dealaggregator.dealapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.dealaggregator.dealapi.entity.Leg;

public interface LegRepository extends JpaRepository<Leg, Long> {
    // No custom methods needed for now!
    // Legs are accessed through their parent Strategy
}