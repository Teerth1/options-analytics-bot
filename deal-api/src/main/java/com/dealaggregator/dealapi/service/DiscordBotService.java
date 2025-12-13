package com.dealaggregator.dealapi.service;

import java.awt.Color;
import java.util.List;

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
            Commands.slash("sell", "Close/Remove a position")
                .addOption(OptionType.STRING, "ticker", "The Ticker to remove (e.g. NVDA)", true),

                // 7. Analyze (Smart Logic)
            Commands.slash("analyze", "Fast Analysis (e.g. NVDA 150c 30d)")
                .addOption(OptionType.STRING, "query", "Format: Ticker Strike+Type Days", true)
                .addOption(OptionType.NUMBER, "volatility", "Volatility (Optional, default 0.4)", false)
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
                StringBuilder sb = new StringBuilder();
                for (Holding h : holdings) {
                    sb.append(String.format("• **%s** $%s %s (Exp: %s) @ **$%.2f**\n",
                        h.getTicker(), h.getStrikePrice(), h.getType(), h.getExpiration(), h.getBuyPrice()));
                }
                eb.setDescription(sb.toString());
            }
            event.replyEmbeds(eb.build()).queue();
        } else if (event.getName().equals("sell")) {
            String ticker = event.getOption("ticker").getAsString().toUpperCase();
            String userId = event.getUser().getName();

            List<Holding> userHoldings = holdingService.getHoldings(userId);
            Holding toRemove = null;
            for (Holding h : userHoldings) {
                if (h.getTicker().equals(ticker)) {
                    toRemove = h;
                    break;
                }
            }
            if (toRemove != null) {
                holdingService.removeHolding(toRemove.getId());
                event.reply("✅ **Closed Position:** Removed " + ticker + " from your portfolio.").queue();
            } else {
                event.reply("❌ You don't have any open positions for **" + ticker + "**.").setEphemeral(true).queue();
            } 
        } else if (event.getName().equals("analyze")) {
            String query = event.getOption("query").getAsString();
            double volatility = 0.4; // Default volatility
            if (event.getOption("volatility") != null) {
                volatility = event.getOption("volatility").getAsDouble();
            }
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
                event.getChannel().sendMessageEmbeds(eb.build()).queue();
            } catch (Exception e) {
                event.reply("❌ Error: " + e.getMessage() + "\nTry format: `NVDA 150c 30d`").setEphemeral(true).queue();
            }
        }   
            
            
    }


}
