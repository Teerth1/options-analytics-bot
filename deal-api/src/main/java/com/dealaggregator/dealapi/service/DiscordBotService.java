package com.dealaggregator.dealapi.service;

import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.dealaggregator.dealapi.entity.Strategy;
import com.dealaggregator.dealapi.entity.StrategyType;
import com.dealaggregator.dealapi.entity.Leg;
import com.dealaggregator.dealapi.entity.CommandLog;
import com.dealaggregator.dealapi.repository.CommandLogRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Service class for Discord bot integration.
 *
 * This service creates and manages a Discord bot using the JDA (Java Discord
 * API)
 * library. The bot provides slash commands for checking stock prices and
 * product
 * deals. It extends ListenerAdapter to handle Discord events.
 *
 * The bot is automatically started when the Spring application initializes
 * via the @PostConstruct annotation.
 */
@Service
public class DiscordBotService extends ListenerAdapter {
    /**
     * Discord bot token loaded from application.properties.
     * Required for authenticating the bot with Discord's API.
     */
    @Value("${discord.bot.token}")
    private String botToken;

    /**
     * Discord channel ID loaded from application.properties.
     * Specifies which channel the bot should interact with.
     */
    @Value("${discord.bot.channel}")
    private String channelId;

    @Value("${discord.bot.guild}")
    private String guildId;

    private final BlackScholesService bsService;
    private final CommandParserService parserService;
    private final MarketDataService marketService;
    private final MassiveDataService massiveService;
    private final StrategyService strategyService;
    private final CommandLogRepository commandLogRepo;

    /**
     * Constructor for DiscordBotService with dependency injection.
     */
    public DiscordBotService(BlackScholesService bsService, CommandParserService parserService,
            MarketDataService marketDataService, MassiveDataService massiveService,
            StrategyService strategyService, CommandLogRepository commandLogRepo) {
        this.bsService = bsService;
        this.parserService = parserService;
        this.marketService = marketDataService;
        this.massiveService = massiveService;
        this.strategyService = strategyService;
        this.commandLogRepo = commandLogRepo;
    }

    /**
     * Initializes and starts the Discord bot.
     *
     * This method is automatically called after dependency injection is complete.
     * It builds the JDA instance with the bot token, registers event listeners,
     * sets the bot's activity status, and registers slash commands.
     *
     * Registered slash commands:
     * - /stock <ticker> : Check a stock price by ticker symbol
     * - /price <product> : Check product deals by product name
     *
     * @throws InterruptedException if the bot startup is interrupted
     */
    @PostConstruct
    public void startBot() throws InterruptedException {
        // Build and configure the JDA instance
        JDA jda = JDABuilder.createDefault(botToken)
                .addEventListeners(this)
                .setActivity(Activity.watching("Market Trends"))
                .build();

        // Wait for the bot to be fully initialized
        jda.awaitReady();

        // Register slash commands to specific guild (server) for instant updates
        // If you want global commands (takes 1 hour to update), use
        // jda.updateCommands() instead
        jda.getGuildById(guildId).updateCommands().addCommands(
                // Stock price checking command
                Commands.slash("stock", "Check a stock price")
                        .addOption(OptionType.STRING, "ticker", "The symbol (e.g. AAPL)", true),

                // Calculate option price command
                Commands.slash("optionprice", "Calculate Black-Scholes option price")
                        .addOption(OptionType.NUMBER, "stockprice", "Current stock price", true)
                        .addOption(OptionType.NUMBER, "strikeprice", "Option strike price", true)
                        .addOption(OptionType.INTEGER, "daystoexpire", "Days to expiration", true)
                        .addOption(OptionType.NUMBER, "volatility", "Stock volatility (e.g. 0.2 for 20%)", true),

                // Product deal checking command
                Commands.slash("price", "Check a product deal")
                        .addOption(OptionType.STRING, "product", "Product name", true),

                Commands.slash("buy", "Quickly add a contract (e.g. NVDA 150c 30d)")
                        .addOption(OptionType.STRING, "contract",
                                "Format: Ticker Strike+Type Days (e.g. NVDA 150c 30d)", true)
                        .addOption(OptionType.NUMBER, "price", "The price you paid (e.g. 1.50)", true),
                // 5. Portfolio View (REQUIRED for Sell)
                Commands.slash("portfolio", "View your active positions"),

                // 6. Sell Position (REQUIRED for Sell)
                Commands.slash("sell", "Close a specific position by ID")
                        .addOption(OptionType.INTEGER, "id", "Position ID from /portfolio", true),

                // 7. Sell All Positions
                Commands.slash("sellall", "Close all positions for a ticker")
                        .addOption(OptionType.STRING, "ticker", "Ticker symbol", true),

                // 8. Analyze (Smart Logic)
                Commands.slash("analyze", "Analyze your portfolio or a specific contract")
                        .addOption(OptionType.STRING, "query",
                                "Optional: Contract (e.g. NVDA 150c 30d). Leave empty to analyze portfolio", false)
                        .addOption(OptionType.NUMBER, "volatility", "Optional: Custom volatility (default 0.4)", false),

                // 9. View Another User's Portfolio
                Commands.slash("view", "View another user's portfolio")
                        .addOption(OptionType.STRING, "username", "Discord username", true),

                // 10. Liquidity Check
                Commands.slash("liquidity", "Check liquidity for a specific contract")
                        .addOption(OptionType.STRING, "contract", "Contract (e.g. NVDA 150c 30d)", true),

                // 11. Smart Spread Command - Auto-generates legs based on strategy type
                Commands.slash("spread", "Open a spread with auto-generated legs")
                        .addOption(OptionType.STRING, "type", "FLY, VERTICAL, STRADDLE, IRON_CONDOR", true)
                        .addOption(OptionType.STRING, "ticker", "Underlying symbol (e.g. SPX, NVDA)", true)
                        .addOption(OptionType.STRING, "strikes", "Strikes with c/p: '6820c 6860c' or '150p 160p'", true)
                        .addOption(OptionType.STRING, "expiry", "Days to expiration (e.g. 30d)", true)
                        .addOption(OptionType.NUMBER, "cost", "Net debit/credit for the spread", true),

                // 12. Stats Command - View bot usage metrics
                Commands.slash("stats", "View bot usage statistics and capacity"))
                .queue();

    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Log every command for usage metrics
        logCommand(event.getName(), event.getUser().getName());

        if (event.getName().equals("stock")) {
            stockSlash(event);
        } else if (event.getName().equals("price")) {
            priceSlash(event);
        } else if (event.getName().equals("optionprice")) {
            optionPriceSlash(event);
        } else if (event.getName().equals("buy")) {
            buySlash(event);
        } else if (event.getName().equals("portfolio")) {
            portfolioSlash(event);
        } else if (event.getName().equals("sell")) {
            sellSlash(event);
        } else if (event.getName().equals("sellall")) {
            sellAllSlash(event);
        } else if (event.getName().equals("analyze")) {
            analyzerSlash(event);
        } else if (event.getName().equals("view")) {
            viewSlash(event);
        } else if (event.getName().equals("liquidity")) {
            liquiditySlash(event);
        } else if (event.getName().equals("spread")) {
            spreadSlash(event);
        } else if (event.getName().equals("stats")) {
            statsSlash(event);
        }
    }

    /**
     * Log a command execution for analytics.
     */
    private void logCommand(String command, String userId) {
        try {
            commandLogRepo.save(new CommandLog(command, userId));
        } catch (Exception e) {
            // Don't fail the command if logging fails
            e.printStackTrace();
        }
    }

    /**
     * Handle /stats command - Show bot usage metrics and capacity.
     */
    private void statsSlash(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        try {
            // Get actual usage metrics
            LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
            LocalDateTime startOfWeek = LocalDate.now().minusDays(7).atStartOfDay();

            long commandsToday = commandLogRepo.countToday(startOfToday);
            long commandsThisWeek = commandLogRepo.countByTimestampAfter(startOfWeek);
            long totalCommands = commandLogRepo.count();
            long uniqueUsers = commandLogRepo.countDistinctUsers();

            // System capacity (based on Railway's free tier)
            long capacityPerDay = 10000; // Conservative estimate

            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("📊 Bot Statistics & Capacity");
            eb.setColor(Color.decode("#3498db"));

            // Actual usage
            eb.addField("📈 Usage",
                    "Today: **" + commandsToday + "** commands\n" +
                            "This Week: **" + commandsThisWeek + "** commands\n" +
                            "All Time: **" + totalCommands + "** commands",
                    true);

            // Capacity
            eb.addField("⚡ System Capacity",
                    "Daily Limit: **" + String.format("%,d", capacityPerDay) + "** commands\n" +
                            "Active Users: **" + uniqueUsers + "**\n" +
                            "Status: **🟢 Healthy**",
                    true);

            // Top commands
            java.util.List<Object[]> topCommands = commandLogRepo.countByCommandGrouped();
            if (!topCommands.isEmpty()) {
                StringBuilder topSb = new StringBuilder();
                int shown = 0;
                for (Object[] row : topCommands) {
                    if (shown >= 5)
                        break;
                    topSb.append("/" + row[0] + ": " + row[1] + "\n");
                    shown++;
                }
                eb.addField("🏆 Top Commands", topSb.toString(), false);
            }

            eb.setFooter("Built to scale • Railway + PostgreSQL");
            event.getHook().sendMessageEmbeds(eb.build()).queue();

        } catch (Exception e) {
            e.printStackTrace();
            event.getHook().sendMessage("❌ Error fetching stats: " + e.getMessage()).queue();
        }
    }

    private void stockSlash(SlashCommandInteractionEvent event) {
        String ticker = event.getOption("ticker").getAsString();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("📊 Market Data: " + ticker);
        eb.setColor(java.awt.Color.CYAN);
        eb.setImage("https://charts2.finviz.com/chart.ashx?t=" + ticker); // The Finviz Chart Trick
        eb.setFooter("Real-time Data via DealAggregator");

        event.replyEmbeds(eb.build()).queue();
    }

    private void priceSlash(SlashCommandInteractionEvent event) {
        String query = event.getOption("product").getAsString();
        // Just a simple reply for now until you implement the full DB search
        event.reply("🔍 Searching database for deals on: **" + query + "**...").setEphemeral(true).queue();
    }

    private void liquiditySlash(SlashCommandInteractionEvent event) {
        String query = event.getOption("contract").getAsString();

        // 1. Acknowledge immediately (ephemeral = visible only to user, false =
        // everyone)
        event.deferReply(false).queue();

        try {
            CommandParserService.ParsedOption opt = parserService.parse(query);

            // 2. Fetch Data (this might take > 3 seconds, so deferReply saved us)
            Optional<MassiveDataService.OptionSnapshot> snapshotOpt = massiveService.getOptionSnapshot(opt.ticker,
                    opt.strike, opt.type, opt.days);

            if (snapshotOpt.isPresent()) {
                MassiveDataService.OptionSnapshot snap = snapshotOpt.get();

                // Liquidity Rating
                String rating = "❓ UNKNOWN";
                if (snap.getOpenInterest() > 5000 && snap.getVolume() > 500)
                    rating = "✅ EXCELLENT";
                else if (snap.getOpenInterest() > 1000)
                    rating = "⚠️ GOOD";
                else
                    rating = "🔴 POOR";

                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("📈 Liquidity Check: " + query);
                eb.setColor(Color.CYAN);
                eb.addField("Volume", String.valueOf(snap.getVolume()), true);
                eb.addField("Open Interest", String.valueOf(snap.getOpenInterest()), true);
                eb.addField("Rating", rating, false);
                eb.setFooter("Spread: $" + String.format("%.2f", (snap.getAsk() - snap.getBid())));

                // 3. Send Result (using hook because we deferred)
                // setEphemeral(false) here won't work if deferReply was true.
                // Currently defaults to ephemeral per deferReply(true).
                event.getHook().sendMessageEmbeds(eb.build()).queue();
            } else {
                event.getHook().sendMessage("❌ No data found for this contract.").queue();
            }
        } catch (Exception e) {
            // Safe error handling
            event.getHook().sendMessage("❌ Error: " + e.getMessage()).queue();
        }
    }

    private void sellSlash(SlashCommandInteractionEvent event) {
        long id = event.getOption("id").getAsLong();
        String userId = event.getUser().getName();

        event.deferReply().queue();

        try {
            // Get the strategy to verify ownership and show details
            List<Strategy> userStrategies = strategyService.getOpenStrategies(userId);
            Strategy target = null;

            for (Strategy s : userStrategies) {
                if (s.getId() == id) {
                    target = s;
                    break;
                }
            }

            if (target != null) {
                strategyService.closeStrategy(id);
                event.getHook().sendMessage(
                        "✅ **Closed Strategy #" + id + ":** " + target.getTicker() +
                                " (" + target.getStrategy() + ") - " + target.getLegs().size() + " leg(s)")
                        .queue();
            } else {
                event.getHook().sendMessage("❌ Strategy #" + id + " not found or doesn't belong to you.").queue();
            }
        } catch (Exception e) {
            e.printStackTrace();
            event.getHook().sendMessage("❌ Error closing strategy: " + e.getMessage()).queue();
        }
    }

    private void buySlash(SlashCommandInteractionEvent event) {
        String query = event.getOption("contract").getAsString();
        double price = event.getOption("price").getAsDouble();
        String userId = event.getUser().getName();

        // Defer reply to prevent timeout during database operations
        event.deferReply().queue();

        try {
            CommandParserService.ParsedOption opt = parserService.parse(query);

            // Calculate expiration date from days
            LocalDate expiration = LocalDate.now().plusDays(opt.days);

            // Create a single leg
            Leg leg = new Leg(opt.type, opt.strike, expiration, price, 1); // quantity = 1 (long)

            // Create strategy with one leg
            Strategy strategy = strategyService.openStrategy(
                    userId,
                    "SINGLE",
                    opt.ticker,
                    List.of(leg));

            event.getHook().sendMessage(
                    "✅ **Position Opened:** " + opt.ticker + " $" + opt.strike + " " + opt.type.toUpperCase() +
                            " (Exp: " + expiration + ") @ $" + price +
                            "\n📋 Strategy ID: " + strategy.getId())
                    .queue();

        } catch (Exception e) {
            e.printStackTrace(); // Log for debugging
            event.getHook().sendMessage("❌ Error: " + e.getMessage() + "\nTry format: `NVDA 150c 30d`").queue();
        }
    }

    /**
     * Handle /spread command with smart templates.
     * Auto-generates legs based on strategy type.
     */
    private void spreadSlash(SlashCommandInteractionEvent event) {
        String typeInput = event.getOption("type").getAsString();
        String ticker = event.getOption("ticker").getAsString().toUpperCase();
        String strikesInput = event.getOption("strikes").getAsString();
        String expiryInput = event.getOption("expiry").getAsString();
        double netCost = event.getOption("cost").getAsDouble();
        String userId = event.getUser().getName();

        event.deferReply().queue();

        try {
            // Parse inputs
            StrategyType strategyType = StrategyType.fromString(typeInput);
            LocalDate expiration = parseExpiry(expiryInput);
            String[] strikes = strikesInput.trim().split("\\s+");

            // Generate legs and create strategy
            ArrayList<Leg> legs = generateLegs(strategyType, strikes, expiration);
            Strategy strategy = strategyService.openStrategy(userId, strategyType.name(), ticker, legs);

            // Build and send response
            String response = buildSpreadResponse(strategyType, ticker, legs, netCost, expiration, strategy.getId());
            event.getHook().sendMessage(response).queue();

        } catch (Exception e) {
            e.printStackTrace();
            event.getHook().sendMessage("❌ Error: " + e.getMessage() +
                    "\nFormat: `/spread fly SPX 6820c 6860c 30d 2.50`").queue();
        }
    }

    /**
     * Parse expiry string like "30d" into LocalDate.
     */
    private LocalDate parseExpiry(String expiryInput) {
        String daysStr = expiryInput.toLowerCase().replace("d", "");
        int days = Integer.parseInt(daysStr);
        return LocalDate.now().plusDays(days);
    }

    /**
     * Build the Discord response message for a spread.
     */
    private String buildSpreadResponse(StrategyType type, String ticker, ArrayList<Leg> legs,
            double netCost, LocalDate expiration, Long strategyId) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ **" + type + " Opened:** " + ticker + "\n");

        for (Leg leg : legs) {
            String direction = leg.getQuantity() > 0 ? "📈 LONG" : "📉 SHORT";
            int qty = Math.abs(leg.getQuantity());
            String qtyStr = qty > 1 ? " x" + qty : "";
            sb.append(direction + qtyStr + " $" + leg.getStrikePrice().intValue() + " " +
                    leg.getOptionType().toUpperCase() + "\n");
        }

        sb.append("💰 Net Cost: $" + String.format("%.2f", netCost) + "\n");
        sb.append("📅 Expires: " + expiration + "\n");
        sb.append("📋 Strategy ID: " + strategyId);

        return sb.toString();
    }

    /**
     * Generate legs based on strategy type.
     * Uses StrategyType enum for better type safety and readability.
     */
    private ArrayList<Leg> generateLegs(StrategyType type, String[] strikes, LocalDate expiration) {
        ArrayList<Leg> legs = new ArrayList<>();

        switch (type) {
            case FLY:
                validateStrikeCount(strikes, 2, "Butterfly");
                legs.addAll(generateButterfly(strikes[0], strikes[1], expiration));
                break;

            case VERTICAL:
                validateStrikeCount(strikes, 2, "Vertical spread");
                legs.addAll(generateVertical(strikes[0], strikes[1], expiration));
                break;

            case STRADDLE:
                validateStrikeCount(strikes, 1, "Straddle");
                legs.addAll(generateStraddle(strikes[0], expiration));
                break;

            case IRON_CONDOR:
                validateStrikeCount(strikes, 4, "Iron Condor");
                legs.addAll(generateIronCondor(strikes, expiration));
                break;

            default:
                throw new IllegalArgumentException("Unsupported strategy type: " + type);
        }

        return legs;
    }

    /**
     * Validate that we have enough strikes for the strategy type.
     */
    private void validateStrikeCount(String[] strikes, int required, String strategyName) {
        if (strikes.length < required) {
            throw new IllegalArgumentException(strategyName + " needs " + required + " strike(s)");
        }
    }

    /**
     * Generate butterfly legs: Buy low, Sell 2x middle, Buy high
     */
    private ArrayList<Leg> generateButterfly(String lowStrike, String highStrike,
            LocalDate expiration) {

        ArrayList<Leg> legs = new ArrayList<>();

        // Parse low and high strikes
        double low = parseStrikeValue(lowStrike);
        double high = parseStrikeValue(highStrike);
        double middle = (low + high) / 2; // Auto-calculate middle
        String optionType = parseOptionType(lowStrike);

        // Buy low strike (long)
        legs.add(new Leg(optionType, low, expiration, 0.0, 1));

        // Sell 2x middle strike (short)
        legs.add(new Leg(optionType, middle, expiration, 0.0, -2));

        // Buy high strike (long)
        legs.add(new Leg(optionType, high, expiration, 0.0, 1));

        return legs;
    }

    /**
     * Generate vertical spread: Buy low, Sell high
     */
    private ArrayList<Leg> generateVertical(String lowStrike, String highStrike,
            LocalDate expiration) {

        ArrayList<Leg> legs = new ArrayList<>();

        double low = parseStrikeValue(lowStrike);
        double high = parseStrikeValue(highStrike);
        String optionType = parseOptionType(lowStrike);

        // Buy low strike (long)
        legs.add(new Leg(optionType, low, expiration, 0.0, 1));

        // Sell high strike (short)
        legs.add(new Leg(optionType, high, expiration, 0.0, -1));

        return legs;
    }

    /**
     * Generate straddle: Buy call + Buy put at same strike
     */
    private ArrayList<Leg> generateStraddle(String strike, LocalDate expiration) {
        ArrayList<Leg> legs = new ArrayList<>();

        double strikePrice = parseStrikeValue(strike);

        // Buy call
        legs.add(new Leg("call", strikePrice, expiration, 0.0, 1));

        // Buy put
        legs.add(new Leg("put", strikePrice, expiration, 0.0, 1));

        return legs;
    }

    /**
     * Generate iron condor: 4 legs
     * Strikes order: put_buy, put_sell, call_sell, call_buy
     */
    private ArrayList<Leg> generateIronCondor(String[] strikes, LocalDate expiration) {
        ArrayList<Leg> legs = new ArrayList<>();

        double s1 = parseStrikeValue(strikes[0]); // Buy put (lowest)
        double s2 = parseStrikeValue(strikes[1]); // Sell put
        double s3 = parseStrikeValue(strikes[2]); // Sell call
        double s4 = parseStrikeValue(strikes[3]); // Buy call (highest)

        legs.add(new Leg("put", s1, expiration, 0.0, 1)); // Buy put
        legs.add(new Leg("put", s2, expiration, 0.0, -1)); // Sell put
        legs.add(new Leg("call", s3, expiration, 0.0, -1)); // Sell call
        legs.add(new Leg("call", s4, expiration, 0.0, 1)); // Buy call

        return legs;
    }

    /**
     * Parse strike value from string like "6820c" or "150p"
     */
    private double parseStrikeValue(String strikeStr) {
        String cleaned = strikeStr.toLowerCase().replaceAll("[cp]", "");
        return Double.parseDouble(cleaned);
    }

    /**
     * Parse option type from string like "6820c" or "150p"
     */
    private String parseOptionType(String strikeStr) {
        if (strikeStr.toLowerCase().endsWith("c")) {
            return "call";
        } else if (strikeStr.toLowerCase().endsWith("p")) {
            return "put";
        }
        throw new IllegalArgumentException("Strike must end with 'c' or 'p': " + strikeStr);
    }

    private void sellAllSlash(SlashCommandInteractionEvent event) {
        String ticker = event.getOption("ticker").getAsString().toUpperCase();
        String userId = event.getUser().getName();

        event.deferReply().queue();

        try {
            List<Strategy> strategies = strategyService.getOpenStrategies(userId);
            int closedCount = 0;

            for (Strategy s : strategies) {
                if (s.getTicker().equalsIgnoreCase(ticker)) {
                    strategyService.closeStrategy(s.getId());
                    closedCount++;
                }
            }

            if (closedCount > 0) {
                event.getHook().sendMessage(
                        "✅ Closed all positions for **" + ticker + "** (" + closedCount + " strategies)").queue();
            } else {
                event.getHook().sendMessage("❌ No positions found for **" + ticker + "**").queue();
            }
        } catch (Exception e) {
            e.printStackTrace();
            event.getHook().sendMessage("❌ Error: " + e.getMessage()).queue();
        }
    }

    private void portfolioSlash(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getName();

        // Defer in case database is slow
        event.deferReply().queue();

        List<Strategy> strategies = strategyService.getOpenStrategies(userId);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("💼 " + userId + "'s Portfolio");
        eb.setColor(Color.decode("#2ecc71")); // Green

        if (strategies.isEmpty()) {
            eb.setDescription("No active positions. Use `/buy` to add one.");
        } else {
            StringBuilder sb = new StringBuilder();
            double totalValue = 0;

            for (Strategy s : strategies) {
                // Strategy header
                sb.append("**#" + s.getId() + " " + s.getTicker() + "** (" + s.getStrategy() + ")\n");

                // List each leg
                for (Leg leg : s.getLegs()) {
                    String legDir = leg.getQuantity() > 0 ? "📈" : "📉"; // Long or Short
                    sb.append(legDir + " $" + leg.getStrikePrice() + " " + leg.getOptionType().toUpperCase() +
                            " @ $" + leg.getEntryPrice() + " (Exp: " + leg.getExpiration() + ")\n");
                    totalValue += leg.getEntryPrice() * leg.getQuantity();
                }
                sb.append("\n");
            }

            eb.setDescription(sb.toString());
            eb.setFooter("Total positions: " + strategies.size() + " | Use /sell <id> to close");
        }
        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }

    private void optionPriceSlash(SlashCommandInteractionEvent event) {
        // Get the inputs (Discord guarantees these are numbers now!)
        double stockPrice = event.getOption("stockprice").getAsDouble();
        double strikePrice = event.getOption("strikeprice").getAsDouble();
        int days = event.getOption("daystoexpire").getAsInt();
        double volatility = event.getOption("volatility").getAsDouble();

        // Convert days to years and use standard risk-free rate
        double timeInYears = days / 365.0;
        double riskFreeRate = 0.05; // 5% risk-free rate

        // Run the Math
        double fairValue = bsService.blackScholes(stockPrice, strikePrice, timeInYears, volatility, riskFreeRate,
                "call");

        // Build the Result Card
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("🧮 Option Fair Value Calculator");
        eb.setColor(java.awt.Color.ORANGE);

        // Show Inputs
        eb.addField("Market Data",
                "Stock: $" + stockPrice + "\nStrike: $" + strikePrice + "\nVol: " + (volatility * 100) + "%",
                false);

        // Show Result
        eb.addField("Theoretical Call Price", "$" + String.format("%.2f", fairValue), false);

        eb.setFooter("Black-Scholes Model via DealAggregator");
        eb.setTimestamp(java.time.Instant.now());

        event.replyEmbeds(eb.build()).queue();
    }

    private void analyzerSlash(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getName();
        double volatility = 0.4; // Default volatility
        if (event.getOption("volatility") != null) {
            volatility = event.getOption("volatility").getAsDouble();
        }

        // Check if query parameter is provided
        if (event.getOption("query") == null) {
            // Mode 1: Analyze entire portfolio
            event.deferReply().queue();

            try {
                List<Strategy> strategies = strategyService.getOpenStrategies(userId);

                if (strategies.isEmpty()) {
                    event.getHook().sendMessage("❌ You have no positions to analyze. Use `/buy` to add contracts!")
                            .queue();
                    return;
                }

                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("📊 Portfolio Analysis for " + userId);
                eb.setColor(Color.decode("#9b59b6")); // Purple

                StringBuilder analysis = new StringBuilder();
                final double vol = volatility; // For lambda

                for (Strategy s : strategies) {
                    for (Leg leg : s.getLegs()) {
                        try {
                            double currentPrice = marketService.getPrice(s.getTicker());
                            if (currentPrice > 0) {
                                long daysToExp = java.time.temporal.ChronoUnit.DAYS.between(
                                        LocalDate.now(),
                                        leg.getExpiration());

                                double fairValue = bsService.blackScholes(
                                        currentPrice,
                                        leg.getStrikePrice(),
                                        daysToExp / 365.0,
                                        vol,
                                        0.05,
                                        leg.getOptionType().toLowerCase());

                                double plPerContract = fairValue - leg.getEntryPrice();
                                double plPercent = (plPerContract / leg.getEntryPrice()) * 100;

                                String plEmoji = plPerContract >= 0 ? "🟢" : "🔴";
                                String plSign = plPerContract >= 0 ? "+" : "";

                                analysis.append(String.format(
                                        "**%s $%.0f %s** (Strategy #%d)\n" +
                                                "Stock: $%.2f | Fair Value: $%.2f\n" +
                                                "Entry: $%.2f | P&L: %s$%.2f (%s%.1f%%) %s\n\n",
                                        s.getTicker(),
                                        leg.getStrikePrice(),
                                        leg.getOptionType().toUpperCase(),
                                        s.getId(),
                                        currentPrice,
                                        fairValue,
                                        leg.getEntryPrice(),
                                        plSign,
                                        plPerContract,
                                        plSign,
                                        plPercent,
                                        plEmoji));
                            }
                        } catch (Exception e) {
                            analysis.append(String.format("**%s** - Could not analyze\n\n", s.getTicker()));
                        }
                    }
                }

                eb.setDescription(analysis.toString());
                eb.setFooter("Analysis uses Black-Scholes with IV=" + (volatility * 100) + "%");
                event.getHook().sendMessageEmbeds(eb.build()).queue();
            } catch (Exception e) {
                e.printStackTrace();
                event.getHook().sendMessage("❌ Error: " + e.getMessage()).queue();
            }

        } else {
            // Mode 2: Analyze specific contract
            String query = event.getOption("query").getAsString();
            event.deferReply().queue();

            try {
                CommandParserService.ParsedOption opt = parserService.parse(query);
                double currentPrice = marketService.getPrice(opt.ticker);
                if (currentPrice == 0.0) {
                    event.getHook().sendMessage("❌ Could not fetch price for **" + opt.ticker + "**.").queue();
                    return;
                }
                double fairValue = bsService.blackScholes(currentPrice, opt.strike, opt.days / 365.0, volatility,
                        0.05, opt.type);

                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("🚀 Fast Analysis: " + opt.ticker + " $" + opt.strike + " " + opt.type.toUpperCase());
                eb.setColor(Color.MAGENTA);
                eb.addField("Live Stock Price", "$" + currentPrice, true);
                eb.addField("Fair Value", "$" + String.format("%.2f", fairValue), true);
                eb.setFooter("Using volatility: " + (volatility * 100) + "%");
                event.getHook().sendMessageEmbeds(eb.build()).queue();
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Error: " + e.getMessage() + "\nTry format: `NVDA 150c 30d`").queue();
            }
        }
    }

    private void viewSlash(SlashCommandInteractionEvent event) {
        String username = event.getOption("username").getAsString();

        event.deferReply().queue();

        try {
            List<Strategy> strategies = strategyService.getOpenStrategies(username);
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("💼 " + username + "'s Portfolio");
            eb.setColor(Color.decode("#3498db")); // Blue

            if (strategies.isEmpty()) {
                eb.setDescription("No active positions.");
            } else {
                StringBuilder sb = new StringBuilder();
                for (Strategy s : strategies) {
                    sb.append("**#" + s.getId() + " " + s.getTicker() + "** (" + s.getStrategy() + ")\n");
                    for (Leg leg : s.getLegs()) {
                        String legDir = leg.getQuantity() > 0 ? "📈" : "📉";
                        sb.append(legDir + " $" + leg.getStrikePrice() + " " + leg.getOptionType().toUpperCase() +
                                " @ $" + leg.getEntryPrice() + "\n");
                    }
                    sb.append("\n");
                }
                eb.setDescription(sb.toString());
            }
            event.getHook().sendMessageEmbeds(eb.build()).queue();
        } catch (Exception e) {
            e.printStackTrace();
            event.getHook().sendMessage("❌ Error: " + e.getMessage()).queue();
        }
    }

}
