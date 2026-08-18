package com.arshraj.vakilconnect;

import com.arshraj.vakilconnect.ai.AiProperties;
import com.arshraj.vakilconnect.ai.document.config.AiDocumentProperties;
import com.arshraj.vakilconnect.email.EmailProperties;
import com.arshraj.vakilconnect.identity.config.IdentityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration-properties classes are registered explicitly here rather than
 * with @ConfigurationPropertiesScan. One line per class, no classpath scanning,
 * and the full set is visible in one place.
 *
 * @EnableScheduling activates @Scheduled methods. The only one today is the
 * email-token purge, which is itself gated by `vakilconnect.identity.purge-enabled`
 * so scheduling can be enabled here without forcing the job to run.
 */
@SpringBootApplication
@EnableConfigurationProperties({ IdentityProperties.class, EmailProperties.class,
		AiProperties.class, AiDocumentProperties.class })
@EnableScheduling
public class VakilconnectApplication {

	public static void main(String[] args) {
		SpringApplication.run(VakilconnectApplication.class, args);
	}

}
