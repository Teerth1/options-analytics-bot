package com.dealaggregator.dealapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Deal Aggregator API.
 *
 * This Spring Boot application provides a REST API for aggregating and managing deals
 * from various online retailers. It includes functionality for user authentication,
 * deal scraping from Reddit, and Discord bot integration.
 *
 * @EnableScheduling - Enables scheduled tasks like automated deal scraping
 * @SpringBootApplication - Marks this as a Spring Boot application with auto-configuration
 */
@SpringBootApplication
@EnableScheduling
public class DealApiApplication {

	/**
	 * Main entry point for the Deal Aggregator API application.
	 *
	 * @param args Command line arguments passed to the application
	 */
	public static void main(String[] args) {
		SpringApplication.run(DealApiApplication.class, args);
	}

}
