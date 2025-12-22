# Your turn! Create the Flask API here.
# 
# Structure to follow:
# 1. Import Flask, jsonify, request
# 2. Import your indicators (calculate_zscore, calculate_half_life, calculate_acf)
# 3. Import fetch_ohlcv from data_fetcher
# 4. Create Flask app
# 5. Create endpoints:
#    - GET /health → returns {"status": "ok"}
#    - GET /indicators/zscore?ticker=SPY → calls calculate_zscore
#    - GET /indicators/halflife?ticker=SPY → calls calculate_half_life  
#    - GET /indicators/acf?ticker=SPY → calls calculate_acf
#    - GET /indicators/all?ticker=SPY → returns all indicators combined
# 6. Run app on port 5001
from flask import Flask, request, jsonify
from indicators import calculate_zscore, calculate_half_life, calculate_acf
from data_fetcher import fetch_ohlcv


app = Flask(__name__)

@app.route('/health')
def health():
    return jsonify({"status": "ok"})


@app.route('/indicators/zscore')
def zscore():
    ticker = request.args.get('ticker')
    if not ticker:
        return jsonify({"error": "Missing ticker parameter"}), 400
    prices = fetch_ohlcv(ticker)['Close'].values
    result = calculate_zscore(prices)
    return jsonify({
        "ticker": ticker,
        "zscore": round(float(result['zscore']), 4),
        "signal": result['signal']
    })

@app.route('/indicators/halflife')
def halflife():
    ticker = request.args.get('ticker')
    if not ticker:
        return jsonify({"error": "Missing ticker parameter"}), 400
    prices = fetch_ohlcv(ticker)['Close'].values
    result = calculate_half_life(prices)
    return jsonify({
        "ticker": ticker,
        "half_life": result['half_life']
    })

@app.route('/indicators/acf')
def acf():
    ticker = request.args.get('ticker')
    if not ticker:
        return jsonify({"error": "Missing ticker parameter"}), 400
    prices = fetch_ohlcv(ticker)['Close'].values
    result = calculate_acf(prices)
    return jsonify({
        "ticker": ticker,
        "acf": result['acf_lag1']
    })

@app.route('/indicators/all')
def all():
    ticker = request.args.get('ticker')
    if not ticker:
        return jsonify({"error": "Missing ticker parameter"}), 400
    prices = fetch_ohlcv(ticker)['Close'].values
    zscore_result = calculate_zscore(prices)
    halflife_result = calculate_half_life(prices)
    acf_result = calculate_acf(prices)
    return jsonify({
        "ticker": ticker,
        "zscore": round(float(zscore_result['zscore']), 4),
        "signal": zscore_result['signal'],
        "half_life": halflife_result['half_life'],
        "acf": acf_result['acf_lag1']
    })

if __name__ == '__main__':
    app.run(port=5001)
