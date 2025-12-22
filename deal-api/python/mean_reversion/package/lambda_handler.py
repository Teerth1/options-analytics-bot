"""
AWS Lambda Handler for Indicators API

This converts the Flask API logic into a Lambda-compatible format.
API Gateway will invoke this handler with HTTP request events.
"""
import json
from indicators import calculate_zscore, calculate_half_life, calculate_acf
from data_fetcher import fetch_ohlcv


def lambda_handler(event, context):
    """
    Main Lambda entry point.
    
    API Gateway sends events like:
    {
        "pathParameters": {"action": "all"},
        "queryStringParameters": {"ticker": "SPY"}
    }
    """
    try:
        # Get path and query params
        path = event.get('pathParameters', {})
        params = event.get('queryStringParameters', {}) or {}
        
        action = path.get('action', 'all')
        ticker = params.get('ticker')
        
        if not ticker:
            return response(400, {"error": "ticker parameter is required"})
        
        # Route to appropriate handler
        if action == 'health':
            return response(200, {"status": "ok"})
        elif action == 'zscore':
            return get_zscore(ticker, params)
        elif action == 'halflife':
            return get_halflife(ticker, params)
        elif action == 'acf':
            return get_acf(ticker, params)
        elif action == 'all':
            return get_all(ticker, params)
        else:
            return response(404, {"error": f"Unknown action: {action}"})
            
    except Exception as e:
        return response(500, {"error": str(e)})


def get_zscore(ticker, params):
    period = params.get('period', '1y')
    lookback = int(params.get('lookback', 50))
    
    df = fetch_ohlcv(ticker, period)
    if df.empty:
        return response(404, {"error": f"No data found for {ticker}"})
    
    result = calculate_zscore(df['Close'], lookback)
    return response(200, {
        "ticker": ticker,
        "zscore": round(float(result['zscore']), 4),
        "signal": result['signal']
    })


def get_halflife(ticker, params):
    period = params.get('period', '2y')
    lookback = int(params.get('lookback', 100))
    
    df = fetch_ohlcv(ticker, period)
    if df.empty:
        return response(404, {"error": f"No data found for {ticker}"})
    
    result = calculate_half_life(df['Close'].values, lookback)
    return response(200, {
        "ticker": ticker,
        "half_life": round(result['half_life'], 2),
        "is_mean_reverting": result['is_mean_reverting']
    })


def get_acf(ticker, params):
    period = params.get('period', '1y')
    max_lag = int(params.get('max_lag', 10))
    
    df = fetch_ohlcv(ticker, period)
    if df.empty:
        return response(404, {"error": f"No data found for {ticker}"})
    
    result = calculate_acf(df['Close'], max_lag)
    return response(200, {
        "ticker": ticker,
        "acf_lag1": round(result['acf_lag1'], 6),
        "is_mean_reverting": result['is_mean_reverting']
    })


def get_all(ticker, params):
    period = params.get('period', '1y')
    
    df = fetch_ohlcv(ticker, period)
    if df.empty:
        return response(404, {"error": f"No data found for {ticker}"})
    
    prices = df['Close']
    
    zscore_result = calculate_zscore(prices, 50)
    halflife_result = calculate_half_life(prices.values, 100)
    acf_result = calculate_acf(prices, 10)
    
    return response(200, {
        "ticker": ticker,
        "zscore": round(float(zscore_result['zscore']), 4),
        "signal": zscore_result['signal'],
        "half_life": round(halflife_result['half_life'], 2),
        "acf": round(acf_result['acf_lag1'], 6)
    })


def response(status_code, body):
    """Build API Gateway compatible response."""
    return {
        "statusCode": status_code,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*"
        },
        "body": json.dumps(body)
    }
