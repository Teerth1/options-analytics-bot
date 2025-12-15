# Options Trading Discord Bot

[![CI/CD Pipeline](https://github.com/Teerth1/deal-aggregator/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/Teerth1/deal-aggregator/actions/workflows/ci-cd.yml)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)

A production-ready Discord bot for options traders. Track positions, calculate fair values with Black-Scholes, analyze portfolios with live P&L, and share strategies with your trading community.

Built with Spring Boot for reliability, PostgreSQL for persistence, and JDA for Discord integration.

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- PostgreSQL 15
- Discord Bot Token ([Get one here](https://discord.com/developers/applications))
- Maven 3.6+

### Setup

1. **Clone the repository**
```bash
git clone https://github.com/Teerth1/deal-aggregator.git
cd deal-aggregator
```

2. **Configure environment variables**

Create a `.env` file in the project root:
```bash
# Discord Configuration
DISCORD_BOT_TOKEN=your_bot_token_here
DISCORD_BOT_CHANNEL=your_channel_id
DISCORD_BOT_GUILD=your_server_id

# PostgreSQL Configuration
PGHOST=localhost
PGPORT=5432
PGDATABASE=dealdb
PGUSER=postgres
PGPASSWORD=your_password

# Massive.com API (Optional - for real market data)
MASSIVE_API_KEY=your_api_key_here
```

3. **Start PostgreSQL**
```bash
docker run --name options-postgres \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=dealdb \
  -p 5432:5432 \
  -d postgres:15
```

4. **Run the bot**
```bash
mvn spring-boot:run
```

The bot will automatically:
- Connect to Discord
- Register slash commands to your guild
- Create database tables via JPA
- Start listening for commands

---

## 💬 Discord Commands

### Portfolio Management
| Command | Description | Example |
|---------|-------------|---------|
| `/buy <contract> <price>` | Add option position to your portfolio | `/buy NVDA 150c 30d 2.50` |
| `/portfolio` | View all your positions with DCA averaging | `/portfolio` |
| `/sell <id>` | Close a specific position by ID | `/sell 42` |
| `/sellall <ticker>` | Close all positions for a ticker | `/sellall NVDA` |

### Analysis & Pricing
| Command | Description | Example |
|---------|-------------|---------|
| `/analyze` | Analyze entire portfolio with live P&L | `/analyze` |
| `/analyze <contract>` | Analyze a specific option contract | `/analyze AAPL 200c 45d` |
| `/optionprice` | Manual Black-Scholes calculator | `/optionprice` (opens form) |
| `/stock <ticker>` | View stock chart with live data | `/stock TSLA` |

### Social Features
| Command | Description | Example |
|---------|-------------|---------|
| `/view <username>` | View another user's portfolio | `/view TradingPro` |

---

## 📊 Key Features

### 1. **Portfolio Analytics with Live P&L**
The `/analyze` command provides real-time profit/loss tracking:

```
📊 Portfolio Analysis for TradingPro
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

NVDA $150 CALL (3 contracts)
Stock: $142.30 | Fair Value: $3.20
Avg Cost: $2.45 | P&L: +$225 (+30.6%) 🟢

AAPL $200 PUT (2 contracts)
Stock: $195.50 | Fair Value: $3.80
Avg Cost: $4.10 | P&L: -$60 (-7.3%) 🔴

Analysis uses Black-Scholes with IV=40%
```

**How it works:**
- Groups contracts by ticker/strike/type
- Calculates dollar-cost average (DCA) for multiple entries
- Fetches live stock prices via MarketDataService
- Computes Black-Scholes theoretical fair value
- Shows unrealized P&L with color-coded indicators

### 2. **Smart Contract Notation**
User-friendly syntax for quick trading:
- `NVDA 150c 30d` = NVIDIA $150 Call expiring in 30 days
- `AAPL 200p 45` = Apple $200 Put expiring in 45 days
- `TSLA 300 20d` = Tesla $300 Call (default) expiring in 20 days

Parsed by `CommandParserService` into structured data for calculations.

### 3. **Black-Scholes Option Pricing**
Industry-standard pricing model implementation:
- Real-time stock price integration
- Configurable volatility (default: 40%)
- Calculates theoretical fair value
- Compares to your entry price for P&L

### 4. **Dollar Cost Averaging (DCA)**
Automatically groups and averages multiple contracts:
```
NVDA $150 CALL (3 contracts) @ avg $2.45
```
Perfect for tracking positions built over time.

---

## 🏗️ Architecture

### Tech Stack
- **Backend:** Spring Boot 3.2.4 (Java 17)
- **Database:** PostgreSQL 15 with JPA/Hibernate
- **Discord API:** JDA 5.0.0-beta.24
- **Caching:** Caffeine (15-minute TTL)
- **Build Tool:** Maven
- **CI/CD:** GitHub Actions

### Project Structure
```
src/
├── main/
│   ├── java/com/dealaggregator/dealapi/
│   │   ├── controller/
│   │   │   └── DealController.java          # REST API for deals
│   │   ├── entity/
│   │   │   ├── Deal.java                    # Reddit deal entity
│   │   │   └── Holding.java                 # Option position entity
│   │   ├── repository/
│   │   │   ├── DealRepository.java          # Spring Data JPA
│   │   │   └── HoldingRepository.java       # Spring Data JPA
│   │   ├── service/
│   │   │   ├── DiscordBotService.java       # Main bot logic
│   │   │   ├── BlackScholesService.java     # Option pricing
│   │   │   ├── CommandParserService.java    # Parse "NVDA 150c 30d"
│   │   │   ├── HoldingService.java          # Portfolio CRUD
│   │   │   ├── MarketDataService.java       # Live stock prices
│   │   │   └── DealScraperService.java      # Reddit scraper
│   │   └── DealApiApplication.java          # Spring Boot entry
│   └── resources/
│       └── application.properties           # Configuration
└── test/                                     # Unit tests
```

### Database Schema

**Holdings Table:**
```sql
CREATE TABLE holdings (
    id SERIAL PRIMARY KEY,
    discord_user_id VARCHAR(255),
    ticker VARCHAR(10),
    type VARCHAR(10),                -- "CALL" or "PUT"
    strike_price DECIMAL,
    expiration DATE,
    buy_price DECIMAL
);
```

**Deals Table:** (Legacy feature)
```sql
CREATE TABLE deals (
    id SERIAL PRIMARY KEY,
    title TEXT,
    price DECIMAL,
    vendor VARCHAR(255),
    deal_url TEXT,
    category VARCHAR(100),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

---

## 🔧 Development

### Running Locally

```bash
# Clean build
mvn clean install

# Run with live reload
mvn spring-boot:run

# Run tests
mvn test

# Package JAR
mvn package
```

### Adding New Commands

1. **Register the command** in `DiscordBotService.java`:
```java
Commands.slash("mycommand", "Description here")
    .addOption(OptionType.STRING, "param", "Param description", true)
```

2. **Add the handler** in `onSlashCommandInteraction()`:
```java
else if (event.getName().equals("mycommand")) {
    String param = event.getOption("param").getAsString();
    // Your logic here
    event.reply("Response").queue();
}
```

3. **Restart the bot** - Commands auto-register on startup

### Environment Configuration

**application.properties:**
```properties
# Discord
discord.bot.token=${DISCORD_BOT_TOKEN}
discord.bot.channel=${DISCORD_BOT_CHANNEL}
discord.bot.guild=${DISCORD_BOT_GUILD}

# PostgreSQL
spring.datasource.url=jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}
spring.datasource.username=${PGUSER}
spring.datasource.password=${PGPASSWORD}

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Caching (Caffeine)
spring.cache.type=caffeine
spring.cache.caffeine.spec=expireAfterWrite=15m,maximumSize=500

# Massive.com API (Optional)
massive.api.key=${MASSIVE_API_KEY}
massive.api.url=https://api.massive.com/v3
```

---

## 🚢 Deployment

### Docker Deployment

```dockerfile
FROM openjdk:17-slim
WORKDIR /app
COPY target/deal-api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# Build
docker build -t options-bot .

# Run
docker run -d \
  --name options-bot \
  -e DISCORD_BOT_TOKEN=your_token \
  -e PGHOST=postgres \
  -p 8080:8080 \
  options-bot
```

### CI/CD Pipeline

GitHub Actions automatically:
1. **Build & Test** - Compiles code and runs unit tests
2. **Code Quality** - Static analysis and linting
3. **Security Scan** - Checks dependencies for vulnerabilities
4. **Docker Build** - Creates container image (on push to master)

See [.github/workflows/ci-cd.yml](.github/workflows/ci-cd.yml)

---

## 📈 Roadmap

### In Progress
- [ ] **Massive.com Integration** - Real market data (bid/ask, IV, Greeks)
- [ ] **Liquidity Metrics** - Open interest, volume, spread analysis

### Planned Features
- [ ] **Options Chain Viewer** - `/chain NVDA 2025-04-17`
- [ ] **Greeks Calculator** - Delta, Gamma, Theta, Vega
- [ ] **IV Rank/Percentile** - Historical volatility analysis
- [ ] **Trade Alerts** - Price targets and expiration reminders
- [ ] **Portfolio Export** - CSV/JSON export functionality
- [ ] **Paper Trading Mode** - Simulated positions for learning

### Community Requested
- [ ] Multi-server support (currently single-guild)
- [ ] Webhook notifications for price alerts
- [ ] Integration with broker APIs (TDAmeritrade, Robinhood)
- [ ] Backtesting historical trades

---

## 🤝 Contributing

Contributions are welcome! Here's how to get started:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style
- Follow Spring Boot best practices
- Use Lombok annotations (`@Data`, `@Service`, etc.)
- Add JavaDoc for public methods
- Write unit tests for new features

The CI/CD pipeline will automatically run tests on your PR.

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **JDA (Java Discord API)** - Discord bot framework
- **Black-Scholes Model** - Fischer Black & Myron Scholes for option pricing theory
- **Finviz** - Free stock charts and technical analysis
- **Yahoo Finance** - Market data API

---

## 📬 Support

- **Issues:** [GitHub Issues](https://github.com/Teerth1/deal-aggregator/issues)
- **Discussions:** [GitHub Discussions](https://github.com/Teerth1/deal-aggregator/discussions)
- **CI/CD Status:** [GitHub Actions](https://github.com/Teerth1/deal-aggregator/actions)

---

**Built with ❤️ using [Claude Code](https://claude.com/claude-code)**
