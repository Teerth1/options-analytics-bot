"""
AWS Lambda Handler for Indicators API

This converts the Flask API logic into a Lambda-compatible format.
API Gateway will invoke this handler with HTTP request events.

Security: Requires X-API-Key header for authentication.
Observability: Structured logging for request tracing.
"""
import json
import os
import logging
from indicators import calculate_zscore, calculate_half_life, calculate_acf
from data_fetcher import fetch_ohlcv

# Configure structured logging for CloudWatch
logger = logging.getLogger()
logger.setLevel(logging.INFO)

# API Key from environment variable (set in Lambda config)
API_KEY = os.environ.get('API_KEY', '')


def lambda_handler(event, context):
    """
    Main Lambda entry point.
    
    Security: Validates X-API-Key header before processing.
    Observability: Logs all requests with context for tracing.
    """
    # Extract request details for logging
    request_id = context.aws_request_id if context else 'local'
    path = event.get('pathParameters', {})
    params = event.get('queryStringParameters', {}) or {}
    headers = event.get('headers', {}) or {}
    
    action = path.get('action', 'all')
    ticker = params.get('ticker', 'unknown')
    
    # Log incoming request (observability)
    logger.info(json.dumps({
        "event": "request_received",
        "request_id": request_id,
        "action": action,
        "ticker": ticker,
        "source_ip": event.get('requestContext', {}).get('identity', {}).get('sourceIp', 'unknown')
    }))
    
    try:
        # API Key Authentication (skip for health check)
        if action != 'health' and API_KEY:
            provided_key = headers.get('x-api-key') or headers.get('X-API-Key', '')
            if provided_key != API_KEY:
                logger.warning(json.dumps({
                    "event": "auth_failed",
                    "request_id": request_id,
                    "reason": "invalid_api_key"
                }))
                return response(401, {"error": "Unauthorized - Invalid API key"})
        
        if not ticker or ticker == 'unknown':
            return response(400, {"error": "ticker parameter is required"})
        
        # Route to appropriate handler
        if action == 'health':
            return response(200, {"status": "ok"})
        elif action == 'zscore':
            result = get_zscore(ticker, params)
        elif action == 'halflife':
            result = get_halflife(ticker, params)
        elif action == 'acf':
            result = get_acf(ticker, params)
        elif action == 'all':
            result = get_all(ticker, params)
        else:
            return response(404, {"error": f"Unknown action: {action}"})
        
        # Log successful response
        logger.info(json.dumps({
            "event": "request_completed",
            "request_id": request_id,
            "action": action,
            "ticker": ticker,
            "status": "success"
        }))
        
        return result
            
    except Exception as e:
        logger.error(json.dumps({
            "event": "request_error",
            "request_id": request_id,
            "error": str(e)
        }))
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
