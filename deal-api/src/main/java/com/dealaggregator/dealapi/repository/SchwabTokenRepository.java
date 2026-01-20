package com.dealaggregator.dealapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.dealaggregator.dealapi.model.SchwabToken;

public interface SchwabTokenRepository extends JpaRepository<SchwabToken, String> {
}
