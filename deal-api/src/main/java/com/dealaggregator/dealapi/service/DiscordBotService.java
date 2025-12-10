package com.dealaggregator.dealapi.service;

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
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    private final DealRepository dealRepo;

    /**
     * Constructor for DiscordBotService with dependency injection.
     *
     * @param dealRepo Repository for accessing deal data from the database
     */
    public DiscordBotService(DealRepository dealRepo) {
        this.dealRepo = dealRepo;
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

        // Register slash commands with Discord
        jda.updateCommands().addCommands(
            // Stock price checking command
            Commands.slash("stock", "Check a stock price")
                .addOption(OptionType.STRING, "ticker", "The symbol (e.g. AAPL)", true),

            // Product deal checking command
            Commands.slash("price", "Check a product deal")
                .addOption(OptionType.STRING, "product", "Product name", true)
        ).queue();
    }
}
