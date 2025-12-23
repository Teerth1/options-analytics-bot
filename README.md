# Options Trading Discord Bot

[![CI Pipeline](https://github.com/Teerth1/options-analytics-bot/actions/workflows/ci.yml/badge.svg)](https://github.com/Teerth1/options-analytics-bot/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange)
![Python](https://img.shields.io/badge/Python-3.12-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen)
![AWS Lambda](https://img.shields.io/badge/AWS-Lambda-FF9900)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)

A production-ready Discord bot for options traders featuring **Black-Scholes pricing**, **mean reversion indicators**, portfolio tracking, and real-time P&L analysis.

**Hybrid Java + Python architecture** with automated CI/CD pipeline deploying to AWS Lambda.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         ARCHITECTURE                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Discord User ──► Java Bot (Railway) ──► AWS Lambda (Python)   │
│                         │                        │               │
│                         ▼                        ▼               │
│                    PostgreSQL            yfinance API            │
│                                                                  │
│   CI/CD: GitHub Actions ──► Tests ──► Docker Build ──► Deploy   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Tech Stack
| Layer | Technology |
|-------|------------|
| **Bot Framework** | Java 17, Spring Boot 3.2.4, JDA 5.0 |
| **Analytics API** | Python 3.12, AWS Lambda, API Gateway |
| **Database** | PostgreSQL 15, JPA/Hibernate |
| **CI/CD** | GitHub Actions, Docker |
| **Hosting** | Railway (Java), AWS Lambda (Python) |
| **Caching** | Caffeine (stock price caching) |

---

## 💬 Discord Commands

### Options Analytics
| Command | Description |
|---------|-------------|
| `/indicator <ticker>` | Mean reversion indicators (Z-Score, Half-Life, ACF) |
| `/analyze` | Portfolio analysis with live P&L |
| `/optionprice` | Black-Scholes fair value calculator |
| `/greeks <contract>` | Delta, Gamma, Theta, Vega calculations |

### Portfolio Management
| Command | Description |
|---------|-------------|
| `/buy <contract> <price>` | Add position (e.g., `NVDA 150c 30d 2.50`) |
| `/portfolio` | View all positions with DCA averaging |
| `/sell <id>` | Close specific position |
| `/spread <type>` | Create multi-leg spreads (bull call, iron condor) |

### Market Data
| Command | Description |
|---------|-------------|
| `/stock <ticker>` | Live stock chart and quote |
| `/view <user>` | View another trader's portfolio |

---

## 🐍 Python Indicators API (AWS Lambda)

Serverless Python API providing mean reversion analytics:

```
GET /all?ticker=SPY
```

**Response:**
```json
{
  "ticker": "SPY",
  "zscore": 1.21,
  "signal": "NEUTRAL",
  "half_life": 10.89,
  "acf": -0.14
}
```

### Indicators
| Metric | Description |
|--------|-------------|
| **Z-Score** | Standard deviations from Kalman-filtered mean |
| **Half-Life** | Mean reversion speed in bars (Ornstein-Uhlenbeck) |
| **ACF Lag-1** | Autocorrelation indicating trend vs mean reversion |
| **Signal** | OVERBOUGHT / OVERSOLD / NEUTRAL |

---

## 🧪 Testing

**14 Unit Tests** covering core business logic:

```bash
mvn test
```

| Test Class | Coverage |
|------------|----------|
| `BlackScholesServiceTest` | Option pricing (8 tests) |
| `StrategyServiceTest` | Portfolio operations (6 tests) |

---

## 🚀 CI/CD Pipeline

GitHub Actions workflow on every push:

```yaml
push to master → Run Tests → Build Docker → Deploy Lambda
```

| Stage | Description |
|-------|-------------|
| **Test** | JUnit 5 + Mockito (14 tests) |
| **Build** | Multi-stage Docker image |
| **Deploy** | Lambda package via AWS CLI |

---

## 🐳 Docker

Multi-stage Dockerfile for optimized images:

```bash
docker build -t options-bot .
docker run -e DISCORD_BOT_TOKEN=xxx -p 8080:8080 options-bot
```

---

## 📁 Project Structure

```
├── deal-api/src/main/java/           # Java Spring Boot
│   └── com/dealaggregator/dealapi/
│       ├── service/
│       │   ├── DiscordBotService     # Discord command handlers
│       │   ├── BlackScholesService   # Option pricing
│       │   ├── IndicatorService      # Calls Python Lambda
│       │   └── StrategyService       # Portfolio CRUD
│       └── entity/                   # JPA entities
│
├── deal-api/python/mean_reversion/   # Python Lambda
│   ├── lambda_handler.py             # AWS Lambda entry point
│   ├── indicators.py                 # Z-Score, Half-Life, ACF
│   └── data_fetcher.py               # yfinance integration
│
├── .github/workflows/ci.yml          # CI/CD pipeline
├── Dockerfile                        # Multi-stage build
└── pom.xml                           # Maven dependencies
```

---

## 🔧 Quick Start

### Prerequisites
- Java 17+, Maven 3.6+
- PostgreSQL 15
- Discord Bot Token

### Setup
```bash
git clone https://github.com/Teerth1/options-analytics-bot.git
cd options-analytics-bot/deal-api

cp .env.example .env
# Edit .env with your tokens

mvn spring-boot:run
```

---

## 📈 Roadmap

- [ ] API Key authentication for Lambda
- [ ] Trade alerts (Discord DM on Z-score threshold)
- [ ] Web dashboard (React/Next.js)
- [ ] Backtesting historical performance

---

## 📝 License

MIT License

---

**Built with Java, Python, AWS, and ❤️**
