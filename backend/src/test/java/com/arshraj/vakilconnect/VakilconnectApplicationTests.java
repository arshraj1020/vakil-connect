package com.arshraj.vakilconnect;

import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Spring context starts against the real schema
 * (Flyway migrations + Hibernate ddl-auto=validate).
 */
class VakilconnectApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
