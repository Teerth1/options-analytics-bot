package com.dealaggregator.dealapi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Logs each Discord command execution for usage analytics.
 * Used to track metrics like "Processing X commands per day".
 */
@Entity
@Data
@Table(name = "command_logs")
public class CommandLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Command name (e.g., "spread", "analyze", "portfolio") */
    private String command;

    /** Discord username who ran the command */
    private String userId;

    /** When the command was executed */
    private LocalDateTime timestamp = LocalDateTime.now();

    /** Whether the command completed successfully */
    private boolean success = true;

    public CommandLog() {
    }

    public CommandLog(String command, String userId) {
        this.command = command;
        this.userId = userId;
    }

    public CommandLog(String command, String userId, boolean success) {
        this.command = command;
        this.userId = userId;
        this.success = success;
    }
}
