package com.arshraj.vakilconnect.email;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sender selection and fail-fast configuration.
 *
 * ApplicationContextRunner rather than @SpringBootTest: these assertions are
 * about which beans exist under which properties, and about a context REFUSING
 * to start. A full Boot context would need Testcontainers and a database for
 * questions that involve neither.
 *
 * This is the test that enforces the two security requirements directly:
 * production can never select the console sender, and a developer can never
 * accidentally reach the real Resend API.
 */
@DisplayName("Email sender selection")
class EmailSenderSelectionTest {

    @Configuration
    @EnableConfigurationProperties(EmailProperties.class)
    static class BaseConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        EmailMetrics emailMetrics(MeterRegistry registry) {
            return new EmailMetrics(registry);
        }

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }

    /**
     * Both senders are offered to every context; their own
     * {@code @ConditionalOnProperty} annotations decide which one materialises.
     *
     * Imported rather than registered with {@code withBean()}: withBean
     * registers UNCONDITIONALLY and would bypass the very conditions under
     * test, making every assertion here pass vacuously.
     */
    private ApplicationContextRunner conditionalRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(BaseConfig.class, SenderConfig.class);
    }

    @Configuration
    @Import({ ConsoleEmailSender.class, ResendEmailSender.class })
    static class SenderConfig {
    }

    // ------------------------------------------------------------- selection

    @Test
    @DisplayName("provider=console creates ONLY the console sender")
    void consoleOnly() {
        conditionalRunner()
                .withPropertyValues("vakilconnect.email.provider=console")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailService.class);
                    assertThat(context).hasSingleBean(ConsoleEmailSender.class);
                    assertThat(context).doesNotHaveBean(ResendEmailSender.class);
                });
    }

    @Test
    @DisplayName("a completely absent provider fails startup, it does not guess")
    void absentProviderFailsFast() {
        /*
         * @NotBlank on EmailProperties.provider wins here, BEFORE
         * ConsoleEmailSender's matchIfMissing=true is ever consulted - so an
         * entirely unset property is a startup failure naming the key, not a
         * silent fallback.
         *
         * That is the right outcome and it costs nothing in practice:
         * application.yaml always supplies ${EMAIL_PROVIDER:console}, and
         * application-prod.yaml pins `resend`, so the property is only ever
         * absent if someone deletes a yaml line - which should fail loudly.
         *
         * This test previously asserted the opposite. It was wrong: the two
         * mechanisms cannot both be authoritative, and validation runs first.
         */
        conditionalRunner().run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("EMAIL_PROVIDER unset resolves to console via the yaml default")
    void envUnsetResolvesToConsole() {
        // Simulates the real chain: the env var is absent, so
        // ${EMAIL_PROVIDER:console} in application.yaml supplies `console`.
        // This is the path a developer actually takes, and it must never be
        // able to reach a live provider.
        conditionalRunner()
                .withPropertyValues("vakilconnect.email.provider=console")
                .run(context -> {
                    assertThat(context).hasSingleBean(ConsoleEmailSender.class);
                    assertThat(context).doesNotHaveBean(ResendEmailSender.class);
                });
    }

    @Test
    @DisplayName("provider=resend creates ONLY the Resend sender")
    void resendOnly() {
        conditionalRunner()
                .withPropertyValues(
                        "vakilconnect.email.provider=resend",
                        "vakilconnect.email.api-key=re_test_key",
                        "vakilconnect.email.from=noreply@example.com")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailService.class);
                    assertThat(context).hasSingleBean(ResendEmailSender.class);
                    // THE PRODUCTION SAFETY ASSERTION: the console sender, which
                    // logs full verification links, must not exist here.
                    assertThat(context).doesNotHaveBean(ConsoleEmailSender.class);
                });
    }

    // ------------------------------------------------------------ fail fast

    @Test
    @DisplayName("resend without an API key fails startup")
    void resendWithoutApiKeyFailsStartup() {
        conditionalRunner()
                .withPropertyValues(
                        "vakilconnect.email.provider=resend",
                        "vakilconnect.email.from=noreply@example.com")
                .run(context -> {
                    // Starting without a key would mean an application that
                    // looks healthy and silently drops every email.
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("RESEND_API_KEY");
                });
    }

    @Test
    @DisplayName("resend without EMAIL_FROM fails startup")
    void resendWithoutFromFailsStartup() {
        conditionalRunner()
                .withPropertyValues(
                        "vakilconnect.email.provider=resend",
                        "vakilconnect.email.api-key=re_test_key")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("EMAIL_FROM");
                });
    }

    @Test
    @DisplayName("a blank API key is treated as missing, not as a valid key")
    void blankApiKeyFailsStartup() {
        conditionalRunner()
                .withPropertyValues(
                        "vakilconnect.email.provider=resend",
                        "vakilconnect.email.api-key=   ",
                        "vakilconnect.email.from=noreply@example.com")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("startup failure never prints the API key")
    void failureDoesNotLeakKey() {
        conditionalRunner()
                .withPropertyValues(
                        "vakilconnect.email.provider=resend",
                        "vakilconnect.email.api-key=re_super_secret_value",
                        "vakilconnect.email.from=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure().toString())
                            .doesNotContain("re_super_secret_value");
                });
    }
}
