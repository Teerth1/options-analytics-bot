"""
Data Fetcher - Gets historical price data for backtesting.

This module provides functions to fetch OHLCV (Open, High, Low, Close, Volume)
data from Yahoo Finance using the yfinance library.

Usage:
    df = fetch_ohlcv("SPY", "2y", "1d")  # 2 years of daily data
    df = fetch_ohlcv("TSLA", "7d", "15m")  # 7 days of 15-minute data
"""
import yfinance as yf
import pandas as pd


def fetch_ohlcv(ticker, period="2y", interval="1d"):
    """
    Fetch historical OHLCV data for a ticker.
    
    Args:
        ticker: Stock symbol (e.g., "SPY", "TSLA", "NVDA")
        period: How far back to fetch (e.g., "1y", "2y", "7d", "1mo")
        interval: Bar size (e.g., "1d", "1h", "15m", "5m")
    
    Returns:
        pandas DataFrame with columns: Open, High, Low, Close, Volume
        Index is datetime.
    
    Example:
        >>> df = fetch_ohlcv("SPY", "1y")
        >>> print(df['Close'].iloc[-1])  # Latest price
    """
    return yf.Ticker(ticker).history(period=period, interval=interval)

if __name__ == "__main__":
    # Test daily
    df = fetch_ohlcv("SPY", "1y")
    print(f"Daily: {df.shape}")
    
    # Test intraday
    df2 = fetch_ohlcv("SPY", "7d", "15m")
    print(f"15-min: {df2.shape}")