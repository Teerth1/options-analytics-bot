package com.dealaggregator.dealapi.service;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.dealaggregator.dealapi.entity.Holding;
import com.dealaggregator.dealapi.repository.DealRepository;

import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class for Discord bot integration.
 *
 * This service creates and manages a Discord bot using the JDA (Java Discord API)
 * library. The bot provides slash commands for checking stock prices and product
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

    private final DealRepository dealRepo;

    private final BlackScholesService bsService;
    private final CommandParserService parserService;
    private final MarketDataService marketService;
    private final HoldingService holdingService;

    /**
     * Constructor for DiscordBotService with dependency injection.
     *
     * @param dealRepo Repository for accessing deal data from the database
     */
    public DiscordBotService(DealRepository dealRepo, BlackScholesService bsService, CommandParserService parserService, MarketDataService marketDataService, HoldingService holdingService) {
        this.dealRepo = dealRepo;
        this.bsService = bsService;
        this.parserService = parserService;
        this.marketService = marketDataService;
        this.holdingService = holdingService;

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
        // If you want global commands (takes 1 hour to update), use jda.updateCommands() instead
        jda.getGuildById(guildId).updateCommands().addCommands(
            // Stock price checking command
            Commands.slash("stock", "Check a stock price")
                .addOption(OptionType.STRING, "ticker", "The symbol (e.g. AAPL)", true),

            //Calculate option price command
            Commands.slash("optionprice", "Calculate Black-Scholes option price")
                .addOption(OptionType.NUMBER, "stockprice", "Current stock price", true)
                .addOption(OptionType.NUMBER, "strikeprice", "Option strike price", true)
                .addOption(OptionType.INTEGER, "daystoexpire", "Days to expiration", true)
                .addOption(OptionType.NUMBER, "volatility", "Stock volatility (e.g. 0.2 for 20%)", true),

            // Product deal checking command
            Commands.slash("price", "Check a product deal")
                .addOption(OptionType.STRING, "product", "Product name", true),

            Commands.slash("buy", "Quickly add a contract (e.g. NVDA 150c 30d)")
                .addOption(OptionType.STRING, "contract", "Format: Ticker Strike+Type Days (e.g. NVDA 150c 30d)", true)
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
                .addOption(OptionType.STRING, "query", "Optional: Contract (e.g. NVDA 150c 30d). Leave empty to analyze portfolio", false)
                .addOption(OptionType.NUMBER, "volatility", "Optional: Custom volatility (default 0.4)", false),

            // 9. View Another User's Portfolio
            Commands.slash("view", "View another user's portfolio")
                .addOption(OptionType.STRING, "username", "Discord username", true)
        ).queue();

    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        
        if (event.getName().equals("stock")) {
            String ticker = event.getOption("ticker").getAsString();
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("📊 Market Data: " + ticker);
            eb.setColor(java.awt.Color.CYAN);
            eb.setImage("https://charts2.finviz.com/chart.ashx?t=" + ticker); // The Finviz Chart Trick
            eb.setFooter("Real-time Data via DealAggregator");
            
            event.replyEmbeds(eb.build()).queue();
        } else if (event.getName().equals("price")) {
            String query = event.getOption("product").getAsString();
            // Just a simple reply for now until you implement the full DB search
            event.reply("🔍 Searching database for deals on: **" + query + "**...").setEphemeral(true).queue();
        } else if (event.getName().equals("optionprice")) {
            // Get the inputs (Discord guarantees these are numbers now!)
            double stockPrice = event.getOption("stockprice").getAsDouble();
            double strikePrice = event.getOption("strikeprice").getAsDouble();
            int days = event.getOption("daystoexpire").getAsInt();
            double volatility = event.getOption("volatility").getAsDouble();

            // Convert days to years and use standard risk-free rate
            double timeInYears = days / 365.0;
            double riskFreeRate = 0.05; // 5% risk-free rate

            // Run the Math
            double fairValue = bsService.blackScholes(stockPrice, strikePrice, timeInYears, volatility, riskFreeRate, "call");

            // Build the Result Card
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("🧮 Option Fair Value Calculator");
            eb.setColor(java.awt.Color.ORANGE);
            
            // Show Inputs
            eb.addField("Market Data", 
                "Stock: $" + stockPrice + "\nStrike: $" + strikePrice + "\nVol: " + (volatility*100) + "%", 
                false);
            
            // Show Result
            eb.addField("Theoretical Call Price", "$" + String.format("%.2f", fairValue), false);
             
            eb.setFooter("Black-Scholes Model via DealAggregator");
            eb.setTimestamp(java.time.Instant.now());

            event.replyEmbeds(eb.build()).queue();
        } else if (event.getName().equals("buy")) {
            String query = event.getOption("contract").getAsString();
            double price = event.getOption("price").getAsDouble();
            String userId = event.getUser().getName();


            try {
                CommandParserService.ParsedOption opt = parserService.parse(query);
                holdingService.addHolding(userId, opt.ticker, opt.type, opt.strike, opt.days, price);
                event.reply("✅ **Added to Portfolio:** " + opt.ticker + " $" + opt.strike + " " + opt.type.toUpperCase() + 
                            " (Exp: " + opt.days + " days) @ $" + price).queue();
            } catch (Exception e) {
                event.reply("❌ Error: " + e.getMessage() + "\nTry format: `NVDA 150c 30d`").setEphemeral(true).queue();
            }

        } else if (event.getName().equals("portfolio")) {
            String userId = event.getUser().getName();
            List<Holding> holdings = holdingService.getHoldings(userId);
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("💼 " + userId + "'s Portfolio");
            eb.setColor(Color.decode("#2ecc71")); // Green

            if (holdings.isEmpty()) {
                eb.setDescription("No active positions. Use `/buy` to add one.");
            } else {
                Map<String, List<Holding>> grouped = new HashMap<>();

                for (Holding h : holdings) {
                    String key = h.getTicker() + "-" + h.getStrikePrice() + "-" + h.getType();
                    if (!grouped.containsKey(key)) {
                        grouped.put(key, new ArrayList<>());
                    }
                    grouped.get(key).add(h);
                }
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, List<Holding>> entry : grouped.entrySet()) {
                    List<Holding> contracts = entry.getValue();
                    Holding first = contracts.get(0);
                    // Calculate average price
                    double totalCost = 0;
                    for (Holding contract : contracts) {
                        totalCost += contract.getBuyPrice();
                    }
                    double avgPrice = totalCost / contracts.size();
                    sb.append(String.format("**%s** $%s %s (%d contracts) @ avg $%.2f\n",
                        first.getTicker(),
                        first.getStrikePrice(),
                        first.getType().toUpperCase(),
                        contracts.size(),
                        avgPrice
                    ));
                }
                eb.setDescription(sb.toString());
                eb.setFooter("Use /sell <id> to close | /sellall <ticker> to close all");
            }
            event.replyEmbeds(eb.build()).queue();
        } else if (event.getName().equals("sell")) {
            long id = event.getOption("id").getAsLong();
            String userId = event.getUser().getName();

            Optional<Holding> holdingOpt = holdingService.getHoldingById(id);

            if (holdingOpt.isPresent() && holdingOpt.get().getDiscordUserId().equals(userId)) {
                Holding h = holdingOpt.get();
                holdingService.removeHolding(id);
                event.reply("✅ Closed position #" + id + ": " + h.getTicker() + " $" + h.getStrikePrice() + " " + h.getType()).queue();
            } else {
                event.reply("❌ Invalid position ID").setEphemeral(true).queue();
            }

        } else if (event.getName().equals("sellall")) {
            String ticker = event.getOption("ticker").getAsString();
            String userId = event.getUser().getName();
            
            int removedCount = holdingService.removeAllHoldingsByTickerAndUser(userId, ticker);

            if (removedCount > 0) {
                event.reply("✅ Closed all positions for **" + ticker.toUpperCase() + "** (" + removedCount + " positions)").queue();
            } else {
                event.reply("❌ No positions found for **" + ticker.toUpperCase() + "**").setEphemeral(true).queue();
            }
            
        } else if (event.getName().equals("analyze")) {
            String userId = event.getUser().getName();
            double volatility = 0.4; // Default volatility
            if (event.getOption("volatility") != null) {
                volatility = event.getOption("volatility").getAsDouble();
            }

            // Check if query parameter is provided
            if (event.getOption("query") == null) {
                // Mode 1: Analyze entire portfolio
                List<Holding> holdings = holdingService.getHoldings(userId);

                if (holdings.isEmpty()) {
                    event.reply("❌ You have no positions to analyze. Use `/buy` to add contracts!").setEphemeral(true).queue();
                    return;
                }

                event.reply("🔍 Analyzing your portfolio...").setEphemeral(true).queue();

                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("📊 Portfolio Analysis for " + userId);
                eb.setColor(Color.decode("#9b59b6")); // Purple

                StringBuilder analysis = new StringBuilder();
                Map<String, List<Holding>> grouped = new HashMap<>();

                // Group by ticker-strike-type
                for (Holding h : holdings) {
                    String key = h.getTicker() + "-" + h.getStrikePrice() + "-" + h.getType();
                    if (!grouped.containsKey(key)) {
                        grouped.put(key, new ArrayList<>());
                    }
                    grouped.get(key).add(h);
                }

                // Analyze each unique position
                for (Map.Entry<String, List<Holding>> entry : grouped.entrySet()) {
                    List<Holding> contracts = entry.getValue();
                    Holding first = contracts.get(0);

                    try {
                        double currentPrice = marketService.getPrice(first.getTicker());
                        if (currentPrice > 0) {
                            // Calculate days to expiration from the expiration date
                            long daysToExp = java.time.temporal.ChronoUnit.DAYS.between(
                                java.time.LocalDate.now(),
                                first.getExpiration()
                            );

                            double fairValue = bsService.blackScholes(
                                currentPrice,
                                first.getStrikePrice(),
                                daysToExp / 365.0,
                                volatility,
                                0.05,
                                first.getType().toLowerCase()
                            );

                            // Calculate average cost
                            double totalCost = 0;
                            for (Holding h : contracts) {
                                totalCost += h.getBuyPrice();
                            }
                            double avgCost = totalCost / contracts.size();

                            // Calculate P&L
                            double plPerContract = fairValue - avgCost;
                            double totalPL = plPerContract * contracts.size();
                            double plPercent = (plPerContract / avgCost) * 100;

                            String plEmoji = totalPL >= 0 ? "🟢" : "🔴";
                            String plSign = totalPL >= 0 ? "+" : "";

                            analysis.append(String.format(
                                "**%s $%s %s** (%d contracts)\n" +
                                "Stock: $%.2f | Fair Value: $%.2f\n" +
                                "Avg Cost: $%.2f | P&L: %s$%.2f (%s%.1f%%) %s\n\n",
                                first.getTicker(),
                                first.getStrikePrice(),
                                first.getType().toUpperCase(),
                                contracts.size(),
                                currentPrice,
                                fairValue,
                                avgCost,
                                plSign,
                                totalPL,
                                plSign,
                                plPercent,
                                plEmoji
                            ));
                        }
                    } catch (Exception e) {
                        analysis.append(String.format("**%s** - Could not analyze\n\n", first.getTicker()));
                    }
                }

                eb.setDescription(analysis.toString());
                eb.setFooter("Analysis uses Black-Scholes with IV=" + (volatility * 100) + "%");
                event.getChannel().sendMessageEmbeds(eb.build()).queue();

            } else {
                // Mode 2: Analyze specific contract
                String query = event.getOption("query").getAsString();
                try {
                    CommandParserService.ParsedOption opt = parserService.parse(query);
                    event.reply("🔍 Fetching live data for **" + opt.ticker + "**...").setEphemeral(true).queue();
                    double currentPrice = marketService.getPrice(opt.ticker);
                    if (currentPrice == 0.0) {
                        event.getChannel().sendMessage("❌ Could not fetch price for **" + opt.ticker + "**.").queue();
                        return;
                    }
                    double fairValue = bsService.blackScholes(currentPrice, opt.strike, opt.days / 365.0, volatility, 0.05, opt.type);

                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("🚀 Fast Analysis: " + opt.ticker + " $" + opt.strike + " " + opt.type.toUpperCase());
                    eb.setColor(Color.MAGENTA);
                    eb.addField("Live Stock Price", "$" + currentPrice, true);
                    eb.addField("Fair Value", "$" + String.format("%.2f", fairValue), true);
                    eb.setFooter("Using volatility: " + (volatility * 100) + "%");
                    event.getChannel().sendMessageEmbeds(eb.build()).queue();
                } catch (Exception e) {
                    event.reply("❌ Error: " + e.getMessage() + "\nTry format: `NVDA 150c 30d`").setEphemeral(true).queue();
                }
            }
        } else if (event.getName().equals("view")) {
            String username = event.getOption("username").getAsString();
            List<Holding> holdings = holdingService.getHoldings(username);
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("💼 " + username + "'s Portfolio");
            eb.setColor(Color.decode("#3498db")); // Blue

            if (holdings.isEmpty()) {
                eb.setDescription("No active positions.");
            } else {
                Map<String, List<Holding>> grouped = new HashMap<>();

                for (Holding h : holdings) {
                    String key = h.getTicker() + "-" + h.getStrikePrice() + "-" + h.getType();
                    if (!grouped.containsKey(key)) {
                        grouped.put(key, new ArrayList<>());
                    }
                    grouped.get(key).add(h);
                }
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, List<Holding>> entry : grouped.entrySet()) {
                    List<Holding> contracts = entry.getValue();
                    Holding first = contracts.get(0);
                    // Calculate average price
                    double totalCost = 0;
                    for (Holding contract : contracts) {
                        totalCost += contract.getBuyPrice();
                    }
                    double avgPrice = totalCost / contracts.size();
                    sb.append(String.format("**%s** $%s %s (%d contracts) @ avg $%.2f\n",
                        first.getTicker(), 
                        first.getStrikePrice(), 
                        first.getType().toUpperCase(),
                        contracts.size(),
                        avgPrice
                    ));
                }
                eb.setDescription(sb.toString());
            }
            event.replyEmbeds(eb.build()).queue();
        }


    }


}

