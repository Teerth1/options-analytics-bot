"""
Indicators - Mean reversion math
"""
import numpy as np
import pandas as pd
def calculate_half_life(prices, lookback=100):
    """
    Calculate half-life of mean reversion.
    Half-life = how many bars until price reverts 50% back to mean.
    Uses Ornstein-Uhlenbeck regression.
    """
    # Step 1: Take log of prices (last N bars)
    log_prices = np.log(prices[-lookback:])
    
    # Step 2: Calculate changes
    # delta_y = today's log price - yesterday's log price
    delta_y = log_prices[1:] - log_prices[:-1]
    
    # lag_y = yesterday's log price (what we regress against)
    lag_y = log_prices[:-1]

    # Step 3: Calculate means
    mean_delta = np.mean(delta_y)
    mean_lag = np.mean(lag_y)
    
    # Step 4: Calculate beta (regression slope)
    # beta = Cov(delta_y, lag_y) / Var(lag_y)
    covariance = np.sum((delta_y - mean_delta) * (lag_y - mean_lag))
    variance = np.sum((lag_y - mean_lag) ** 2)
    
    beta = covariance / variance if variance != 0 else 0


    # Step 5: Calculate half-life
    # Formula: half_life = -ln(2) / ln(1 + beta)
    if beta < 0 and beta > -1:
        half_life = -np.log(2) / np.log(1 + beta)
    else:
        half_life = float('inf')  # Not mean reverting
    
    # Step 6: Return results
    return {
        'half_life': min(half_life, 500),
        'beta': beta,
        'is_mean_reverting': beta < 0 and half_life < 50
    }

class KalmanFilter:
    def __init__(self, process_noise=0.01, measurement_noise=1.0):
        self.Q = process_noise
        self.R = measurement_noise
        self.estimate = None
        self.error_cov = 1.0

    def update(self, observation):
        if self.estimate is None:
            self.estimate = observation
            return self.estimate

        # Prediction step
        predicted_estimate = self.estimate
        predicted_error_cov = self.error_cov + self.Q

        # Update step
        kalman_gain = predicted_error_cov / (predicted_error_cov + self.R)
        self.estimate = predicted_estimate + kalman_gain * (observation - predicted_estimate)
        self.error_cov = (1 - kalman_gain) * predicted_error_cov
        return self.estimate

if __name__ == "__main__":
    from data_fetcher import fetch_ohlcv
    
    df = fetch_ohlcv("SPY", "2y")
    prices = df['Close']
    
    # Test Half-Life
    print("=== Half-Life Test ===")
    result = calculate_half_life(prices)
    print(f"Half-Life: {result['half_life']:.1f} bars")
    print(f"Beta: {result['beta']:.4f}")
    print(f"Mean Reverting: {result['is_mean_reverting']}")
    
    # Test Kalman Filter
    print("\n=== Kalman Filter Test ===")
    kf = KalmanFilter(process_noise=0.01, measurement_noise=1.0)
    
    # Run Kalman on last 10 prices
    print("Price → Kalman Estimate")
    for price in prices[-10:]:
        estimate = kf.update(price)
        print(f"  {price:.2f} → {estimate:.2f}")
    
    # Show how much smoothing happened
    print(f"\nLast price: {prices.iloc[-1]:.2f}")
    print(f"Kalman estimate: {kf.estimate:.2f}")