package com.dealaggregator.dealapi.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.dealaggregator.dealapi.entity.Holding;
import com.dealaggregator.dealapi.repository.HoldingRepository;

@Service
public class HoldingService {

    private final HoldingRepository holdingRepository;

    public HoldingService(HoldingRepository holdingRepository) {
        this.holdingRepository = holdingRepository;
    }

    public Holding addHolding(String userId, String ticker, String type, Double strike, int daysToEx, Double buyPrice) {
        // Calculate the Expiration Date logic here
        LocalDate expirationDate = LocalDate.now().plusDays(daysToEx);

        // Create the new Holding (Purchase Date is effectively "now" implicitly)
        Holding holding = new Holding(userId, ticker.toUpperCase(), type.toUpperCase(), strike, expirationDate, buyPrice);
        
        return holdingRepository.save(holding);
    }

    public List<Holding> getHoldings(String userId) {
        return holdingRepository.findByDiscordUserId(userId);
    }
    
    public Optional<Holding> getHoldingById(Long id) {
        return holdingRepository.findById(id);
    }
    public void removeHolding(Long id) {
        holdingRepository.deleteById(id);
    }


    public int removeAllHoldingsByTickerAndUser(String userId, String ticker) {
        return holdingRepository.deleteByDiscordUserIdAndTicker(userId, ticker.toUpperCase());
    }
}