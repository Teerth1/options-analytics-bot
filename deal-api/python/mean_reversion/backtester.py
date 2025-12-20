"""
Backtester - Simulates mean reversion trading strategy
"""
import numpy as np
import pandas as pd
from dataclasses import dataclass
from typing import List, Optional
from enum import Enum

from data_fetcher import fetch_ohlcv
from indicators import calculate_half_life, calculate_zscore, calculate_acf, KalmanFilter


class Position(Enum):
    """Trading position states"""
    FLAT = 0
    LONG = 1
    SHORT = -1


@dataclass
class Trade:
    """Represents a single completed trade"""
    entry_date: str
    exit_date: str
    entry_price: float
    exit_price: float
    position: str  # "LONG" or "SHORT"
    pnl: float
    pnl_pct: float
    bars_held: int
    exit_reason: str


@dataclass
class BacktestResult:
    """Results from a backtest run"""
    total_trades: int
    winning_trades: int
    losing_trades: int
    win_rate: float
    total_pnl: float
    total_pnl_pct: float
    avg_pnl_per_trade: float
    max_drawdown: float
    sharpe_ratio: float
    profit_factor: float
    avg_bars_held: float
    trades: List[Trade]


class MeanReversionBacktester:
    """
    Backtests a mean reversion strategy using Z-Score signals.
    
    Strategy Logic:
    - BUY when Z-Score < -threshold (oversold)
    - SELL when Z-Score > +threshold (overbought)
    - Exit when Z-Score crosses zero (mean)
    """
    
    def __init__(
        self,
        ticker: str,
        period: str = "2y",
        interval: str = "1d",
        zscore_threshold: float = 2.0,
        zscore_lookback: int = 50,
        stop_loss_pct: float = 0.05,
        take_profit_pct: float = 0.10,
        max_holding_bars: int = 20,
        use_half_life_filter: bool = True,
        half_life_max: int = 50
    ):
        """
        Initialize backtester.
        
        Args:
            ticker: Stock symbol
            period: Data period (e.g., "2y")
            interval: Data interval (e.g., "1d")
            zscore_threshold: Entry threshold for Z-Score
            zscore_lookback: Lookback period for Z-Score
            stop_loss_pct: Stop loss percentage (0.05 = 5%)
            take_profit_pct: Take profit percentage
            max_holding_bars: Maximum bars to hold a position
            use_half_life_filter: Only trade when half-life is reasonable
            half_life_max: Maximum half-life to allow trading
        """
        self.ticker = ticker
        self.period = period
        self.interval = interval
        self.zscore_threshold = zscore_threshold  
        self.zscore_lookback = zscore_lookback
        self.stop_loss_pct = stop_loss_pct
        self.take_profit_pct = take_profit_pct
        self.max_holding_bars = max_holding_bars
        self.use_half_life_filter = use_half_life_filter
        self.half_life_max = half_life_max
        
        # Will be populated when run() is called
        self.df = None
        self.trades: List[Trade] = []
        
    def run(self) -> BacktestResult:
        """Run the backtest and return results."""
        # Fetch data
        self.df = fetch_ohlcv(self.ticker, self.period, self.interval)
        
        if self.df.empty:
            raise ValueError(f"No data for {self.ticker}")
        
        prices = self.df['Close'].values
        dates = self.df.index.astype(str).values
        n = len(prices)
        
        # Pre-calculate indicators
        zscore_series = self._calculate_zscore_series(prices)
        
        # Check half-life if filter is enabled
        regime_ok = True
        if self.use_half_life_filter:
            hl_result = calculate_half_life(pd.Series(prices))
            regime_ok = hl_result['half_life'] < self.half_life_max
        
        # Trading state
        position = Position.FLAT
        entry_price = 0.0
        entry_date = ""
        entry_bar = 0
        
        # Track equity for drawdown calculation
        equity = [1.0]  # Start with $1
        
        self.trades = []
        
        # Start after enough data for indicators
        start_bar = self.zscore_lookback + 10
        
        for i in range(start_bar, n):
            current_price = prices[i]
            current_date = dates[i]
            z = zscore_series[i]
            
            # Skip if NaN
            if np.isnan(z):
                equity.append(equity[-1])
                continue
            
            # ========== EXIT LOGIC ==========
            if position != Position.FLAT:
                bars_held = i - entry_bar
                
                # Calculate current P&L
                if position == Position.LONG:
                    pnl_pct = (current_price - entry_price) / entry_price
                else:  # SHORT
                    pnl_pct = (entry_price - current_price) / entry_price
                
                exit_signal = False
                exit_reason = ""
                
                # Exit conditions
                if position == Position.LONG and z >= 0:
                    exit_signal = True
                    exit_reason = "Z crossed zero"
                elif position == Position.SHORT and z <= 0:
                    exit_signal = True
                    exit_reason = "Z crossed zero"
                elif pnl_pct <= -self.stop_loss_pct:
                    exit_signal = True
                    exit_reason = "Stop loss"
                elif pnl_pct >= self.take_profit_pct:
                    exit_signal = True
                    exit_reason = "Take profit"
                elif bars_held >= self.max_holding_bars:
                    exit_signal = True
                    exit_reason = "Max holding time"
                
                if exit_signal:
                    # Record trade
                    trade = Trade(
                        entry_date=entry_date,
                        exit_date=current_date,
                        entry_price=entry_price,
                        exit_price=current_price,
                        position="LONG" if position == Position.LONG else "SHORT",
                        pnl=current_price - entry_price if position == Position.LONG else entry_price - current_price,
                        pnl_pct=pnl_pct,
                        bars_held=bars_held,
                        exit_reason=exit_reason
                    )
                    self.trades.append(trade)
                    
                    # Update equity
                    equity.append(equity[-1] * (1 + pnl_pct))
                    
                    # Reset position
                    position = Position.FLAT
                    continue
            
            # ========== ENTRY LOGIC ==========
            if position == Position.FLAT and regime_ok:
                # Oversold → Go Long
                if z < -self.zscore_threshold:
                    position = Position.LONG
                    entry_price = current_price
                    entry_date = current_date
                    entry_bar = i
                
                # Overbought → Go Short
                elif z > self.zscore_threshold:
                    position = Position.SHORT
                    entry_price = current_price
                    entry_date = current_date
                    entry_bar = i
            
            # Track equity (no change if no trade)
            if len(equity) <= i - start_bar:
                equity.append(equity[-1])
        
        # Close any open position at end
        if position != Position.FLAT:
            bars_held = n - 1 - entry_bar
            current_price = prices[-1]
            if position == Position.LONG:
                pnl_pct = (current_price - entry_price) / entry_price
            else:
                pnl_pct = (entry_price - current_price) / entry_price
            
            trade = Trade(
                entry_date=entry_date,
                exit_date=dates[-1],
                entry_price=entry_price,
                exit_price=current_price,
                position="LONG" if position == Position.LONG else "SHORT",
                pnl=current_price - entry_price if position == Position.LONG else entry_price - current_price,
                pnl_pct=pnl_pct,
                bars_held=bars_held,
                exit_reason="End of data"
            )
            self.trades.append(trade)
        
        # Calculate results
        return self._calculate_results(equity)
    
    def _calculate_zscore_series(self, prices: np.ndarray) -> np.ndarray:
        """Calculate Z-Score for entire price series."""
        series = pd.Series(prices)
        rolling_mean = series.rolling(window=self.zscore_lookback).mean()
        rolling_std = series.rolling(window=self.zscore_lookback).std()
        zscore = (series - rolling_mean) / rolling_std
        return zscore.values
    
    def _calculate_results(self, equity: List[float]) -> BacktestResult:
        """Calculate backtest statistics."""
        if not self.trades:
            return BacktestResult(
                total_trades=0,
                winning_trades=0,
                losing_trades=0,
                win_rate=0.0,
                total_pnl=0.0,
                total_pnl_pct=0.0,
                avg_pnl_per_trade=0.0,
                max_drawdown=0.0,
                sharpe_ratio=0.0,
                profit_factor=0.0,
                avg_bars_held=0.0,
                trades=[]
            )
        
        winning = [t for t in self.trades if t.pnl > 0]
        losing = [t for t in self.trades if t.pnl <= 0]
        
        total_pnl = sum(t.pnl for t in self.trades)
        total_pnl_pct = sum(t.pnl_pct for t in self.trades)
        
        # Profit factor
        gross_profit = sum(t.pnl for t in winning) if winning else 0
        gross_loss = abs(sum(t.pnl for t in losing)) if losing else 1
        profit_factor = gross_profit / gross_loss if gross_loss > 0 else 0
        
        # Max drawdown
        equity_arr = np.array(equity)
        peak = np.maximum.accumulate(equity_arr)
        drawdown = (peak - equity_arr) / peak
        max_drawdown = np.max(drawdown) if len(drawdown) > 0 else 0
        
        # Sharpe ratio (simplified daily)
        returns = np.diff(equity_arr) / equity_arr[:-1] if len(equity_arr) > 1 else np.array([0])
        sharpe = np.mean(returns) / np.std(returns) * np.sqrt(252) if np.std(returns) > 0 else 0
        
        return BacktestResult(
            total_trades=len(self.trades),
            winning_trades=len(winning),
            losing_trades=len(losing),
            win_rate=len(winning) / len(self.trades) if self.trades else 0,
            total_pnl=total_pnl,
            total_pnl_pct=total_pnl_pct,
            avg_pnl_per_trade=total_pnl / len(self.trades),
            max_drawdown=max_drawdown,
            sharpe_ratio=sharpe,
            profit_factor=profit_factor,
            avg_bars_held=np.mean([t.bars_held for t in self.trades]),
            trades=self.trades
        )
    
    def print_summary(self, result: BacktestResult):
        """Print a formatted summary of results."""
        print(f"\n{'='*50}")
        print(f"BACKTEST RESULTS: {self.ticker}")
        print(f"{'='*50}")
        print(f"Period: {self.period} | Interval: {self.interval}")
        print(f"Z-Score Threshold: {self.zscore_threshold}")
        print(f"{'='*50}")
        print(f"Total Trades:     {result.total_trades}")
        print(f"Winning Trades:   {result.winning_trades}")
        print(f"Losing Trades:    {result.losing_trades}")
        print(f"Win Rate:         {result.win_rate*100:.1f}%")
        print(f"{'='*50}")
        print(f"Total P&L:        ${result.total_pnl:.2f}")
        print(f"Total P&L %:      {result.total_pnl_pct*100:.2f}%")
        print(f"Avg P&L/Trade:    ${result.avg_pnl_per_trade:.2f}")
        print(f"{'='*50}")
        print(f"Max Drawdown:     {result.max_drawdown*100:.2f}%")
        print(f"Sharpe Ratio:     {result.sharpe_ratio:.2f}")
        print(f"Profit Factor:    {result.profit_factor:.2f}")
        print(f"Avg Bars Held:    {result.avg_bars_held:.1f}")
        print(f"{'='*50}")
        
        if result.trades:
            print("\nLast 5 Trades:")
            for trade in result.trades[-5:]:
                print(f"  {trade.position} | Entry: ${trade.entry_price:.2f} → Exit: ${trade.exit_price:.2f} | P&L: {trade.pnl_pct*100:+.2f}% | {trade.exit_reason}")


# Test when run directly
if __name__ == "__main__":
    print("Running backtest on SPY...")
    
    backtester = MeanReversionBacktester(
        ticker="SPY",
        period="2y",
        interval="1d",
        zscore_threshold=2.0,
        stop_loss_pct=0.03,
        take_profit_pct=0.05
    )
    
    try:
        result = backtester.run()
        backtester.print_summary(result)
    except Exception as e:
        print(f"Error: {e}")
