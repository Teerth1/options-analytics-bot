"""
Data Fetcher - Gets price data for backtesting
"""
import yfinance as yf
import pandas as pd



def fetch_ohlcv(ticker, period="2y", interval="1d"):
    return yf.Ticker(ticker).history(period=period, interval=interval)

if __name__ == "__main__":
    # Test daily
    df = fetch_ohlcv("SPY", "1y")
    print(f"Daily: {df.shape}")
    
    # Test intraday
    df2 = fetch_ohlcv("SPY", "7d", "15m")
    print(f"15-min: {df2.shape}")