package com.arshraj.vakilconnect;

import com.arshraj.vakilconnect.identity.config.IdentityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Configuration-properties classes are registered explicitly here rather than
 * with @ConfigurationPropertiesScan. One line per class, no classpath scanning,
 * and the full set is visible in one place.
 */
@SpringBootApplication
@EnableConfigurationProperties(IdentityProperties.class)
public class VakilconnectApplication {

	public static void main(String[] args) {
		SpringApplication.run(VakilconnectApplication.class, args);
	}

}
