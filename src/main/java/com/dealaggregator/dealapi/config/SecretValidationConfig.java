package com.dealaggregator.dealapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecretValidationConfig {

    @Value("${discord.bot.token}")
    private String discordToken;

    @Value("${discord.bot.channel}")
    private String discordChannel;

    @Value("${discord.bot.guild}")
    private String discordGuild;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Bean
    public ApplicationRunner validateSecrets() {
        return args -> {
            List<String> missingSecrets = new ArrayList<>();

            if (isMissingOrPlaceholder(dbPassword, "DB_PASSWORD")) {
                missingSecrets.add("DB_PASSWORD (Database Password)");
            }
            if (isMissingOrPlaceholder(discordToken, "DISCORD_BOT_TOKEN")) {
                missingSecrets.add("DISCORD_BOT_TOKEN (Discord Bot Token)");
            }
            if (isMissingOrPlaceholder(discordChannel, "DISCORD_BOT_CHANNEL")) {
                missingSecrets.add("DISCORD_BOT_CHANNEL (Discord Channel ID)");
            }
            if (isMissingOrPlaceholder(discordGuild, "DISCORD_BOT_GUILD")) {
                missingSecrets.add("DISCORD_BOT_GUILD (Discord Guild ID)");
            }

            if (!missingSecrets.isEmpty()) {
                String message = String.format(
                    "FATAL: Application failed to start because required environment variables are missing.%n" +
                    "Please set the following environment variables before running the application:%n" +
                    " - %s",
                    String.join("%n - ", missingSecrets)
                );
                throw new IllegalStateException(message);
            }
        };
    }

    private boolean isMissingOrPlaceholder(String value, String placeholder) {
        String placeholderValue = String.format("${%s}", placeholder);
        return !StringUtils.hasText(value) || value.equals(placeholderValue);
    }
}
