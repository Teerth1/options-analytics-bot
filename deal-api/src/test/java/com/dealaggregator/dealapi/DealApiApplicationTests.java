package com.dealaggregator.dealapi;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test class for the Deal Aggregator API application.
 *
 * NOTE: This test requires a running database connection.
 * Disabled by default for CI - run manually or with integration test profile.
 */
@SpringBootTest
@Disabled("Requires database connection - run integration tests separately")
class DealApiApplicationTests {

	/**
	 * Basic test to verify the Spring application context loads successfully.
	 *
	 * This test ensures that:
	 * - All Spring beans are created without errors
	 * - All configurations are valid
	 * - The application can start up properly
	 *
	 * If this test fails, it indicates a configuration or dependency issue
	 * preventing the application from starting.
	 */
	@Test
	void contextLoads() {
	}

}
