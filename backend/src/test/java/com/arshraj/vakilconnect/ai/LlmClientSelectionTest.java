package com.arshraj.vakilconnect.ai;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which LlmClient materialises, under which properties.
 *
 * ApplicationContextRunner rather than @SpringBootTest: every assertion here is
 * about which beans exist, and about a context REFUSING to start. A full Boot
 * context would need Testcontainers and a database for questions that involve
 * neither.
 *
 * This is the test that enforces the operational requirements directly: the
 * provider that needs nothing installed is the default, wiring the real provider
 * requires NO API KEY, and no context in the suite reaches an inference server.
 */
@DisplayName("LlmClient provider selection")
class LlmClientSelectionTest {

    @TestConfiguration
    static class MetricsConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        AiMetrics aiMetrics(MeterRegistry registry) {
            return new AiMetrics(registry);
        }
    }

    /**
     * Metrics plus real property binding.
     *
     * Kept SEPARATE from MetricsConfig because @EnableConfigurationProperties
     * makes AiProperties mandatory, and provider is @NotBlank - so a context
     * that deliberately supplies no properties at all (see
     * {@link #defaultsToStub()}) would fail validation before it could
     * demonstrate anything about the fallback.
     */
    @TestConfiguration
    @EnableConfigurationProperties(AiProperties.class)
    static class BaseConfig extends MetricsConfig {
    }

    /**
     * Both clients are OFFERED to every context; their own
     * {@code @ConditionalOnProperty} annotations decide which one materialises.
     *
     * @Import, not withBean(): withBean registers UNCONDITIONALLY and would
     * bypass the very conditions under test, making every assertion here pass
     * vacuously. An @Bean factory method would do the same - a method does not
     * inherit its return type's @ConditionalOnProperty.
     *
     * AiConfig is imported alongside them because OllamaLlmClient depends on the
     * `ollamaRestClient` bean it declares, and it carries the same condition -
     * so a stub context builds no HTTP client at all.
     */
    @TestConfiguration
    @Import({ StubLlmClient.class, OllamaLlmClient.class, AiConfig.class })
    static class ProviderConfig {
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(BaseConfig.class, ProviderConfig.class)
                /*
                 * A plain builder. AiConfig sets a request factory with
                 * timeouts on whatever it is given, and nothing in this class
                 * ever calls complete(), so no request is ever issued to
                 * localhost:11434 or anywhere else.
                 */
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withPropertyValues(
                        "vakilconnect.ai.base-url=http://localhost:11434",
                        "vakilconnect.ai.model=llama3.2",
                        "vakilconnect.ai.temperature=0.2",
                        "vakilconnect.ai.max-output-tokens=1024",
                        "vakilconnect.ai.connect-timeout=PT5S",
                        "vakilconnect.ai.read-timeout=PT120S");
    }

    // ------------------------------------------------------------------ stub

    @Test
    @DisplayName("provider=stub selects the stub and no Ollama bean exists")
    void stubSelected() {
        runner().withPropertyValues("vakilconnect.ai.provider=stub").run(context -> {
            assertThat(context).hasSingleBean(LlmClient.class);
            assertThat(context).hasSingleBean(StubLlmClient.class);
            assertThat(context).doesNotHaveBean(OllamaLlmClient.class);

            // Asserted through behaviour, not through getClass(): a bean can
            // legitimately be a proxy, and a proxy's class is not the
            // implementation's.
            assertThat(context.getBean(LlmClient.class).providerName())
                    .isEqualTo(AiProperties.STUB);
        });
    }

    @Test
    @DisplayName("provider=stub builds no HTTP client at all")
    void stubBuildsNoHttpClient() {
        // The strongest available proof that the test suite cannot reach an
        // inference server: under the default provider there is no configured
        // RestClient in the context for anything to call with.
        runner().withPropertyValues("vakilconnect.ai.provider=stub")
                .run(context -> assertThat(context).doesNotHaveBean(RestClient.class));
    }

    @Test
    @DisplayName("an unset provider falls back to the stub, never to Ollama")
    void defaultsToStub() {
        /*
         * matchIfMissing on StubLlmClient. In the real application this is
         * belt-and-braces - AiProperties.provider is @NotBlank, so an entirely
         * absent property fails validation before any condition is evaluated -
         * but it states the intent: absent configuration yields the provider
         * that needs no server running, no model pulled and no disk space.
         */
        new ApplicationContextRunner()
                // MetricsConfig, not BaseConfig: no AiProperties bean at all,
                // so nothing supplies the provider property. That is the whole
                // point - this asserts what happens with NO configuration.
                .withUserConfiguration(MetricsConfig.class, ProviderConfig.class)
                .withBean(RestClient.Builder.class, RestClient::builder)
                .run(context -> {
                    assertThat(context).hasSingleBean(StubLlmClient.class);
                    assertThat(context).doesNotHaveBean(OllamaLlmClient.class);
                    assertThat(context).doesNotHaveBean(RestClient.class);
                });
    }

    // ---------------------------------------------------------------- ollama

    @Test
    @DisplayName("provider=ollama wires up with NO API KEY of any kind")
    void ollamaSelectedWithoutAnyCredential() {
        /*
         * THE REQUIREMENT THAT MOTIVATED THE WHOLE PROVIDER CHANGE. The only
         * properties set here are a URL and a model - there is no key, no
         * token, no secret, and the context starts anyway. Contrast the adapter
         * this replaced, whose constructor refused to start without a key.
         */
        runner().withPropertyValues("vakilconnect.ai.provider=ollama").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LlmClient.class);
            assertThat(context).hasSingleBean(OllamaLlmClient.class);
            assertThat(context).doesNotHaveBean(StubLlmClient.class);

            assertThat(context.getBean(LlmClient.class).providerName())
                    .isEqualTo(AiProperties.OLLAMA);

            // AiConfig carries the same condition, so the HTTP client exists
            // exactly when the adapter that needs it does.
            assertThat(context).hasSingleBean(RestClient.class);
        });
    }

    @Test
    @DisplayName("starting in ollama mode does NOT require Ollama to be running")
    void ollamaWiringDoesNotProbeTheServer() {
        /*
         * A deliberate design choice, asserted so it is not "improved" into a
         * startup health check later. Ollama is a local tool a developer starts
         * and stops freely; a backend that refused to boot without it would be
         * hostile, and CI has no Ollama at all.
         *
         * The port here is one nothing is listening on. If the adapter probed
         * the server at construction, this context would fail. It starts.
         */
        runner().withPropertyValues(
                        "vakilconnect.ai.provider=ollama",
                        "vakilconnect.ai.base-url=http://127.0.0.1:1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OllamaLlmClient.class);
                });
    }

    @Test
    @DisplayName("a base URL that is not a URL REFUSES TO START")
    void malformedBaseUrlFailsFast() {
        /*
         * The one thing worth failing fast on now that there is no key to
         * check. A missing scheme would throw from URI.create on the first
         * request - a runtime failure for what is unambiguously a startup-time
         * configuration mistake.
         */
        runner().withPropertyValues(
                        "vakilconnect.ai.provider=ollama",
                        "vakilconnect.ai.base-url=localhost:11434")
                .run(context -> {
                    assertThat(context).hasFailed();

                    /*
                     * ASSERTED ON THE WHOLE CHAIN, NOT ON rootCause().
                     *
                     * This is where the original version of this test was
                     * wrong. OllamaLlmClient throws IllegalStateException
                     * WRAPPING the IllegalArgumentException that URI parsing
                     * produced - deliberately, because the cause is what tells
                     * you the URL was unparseable rather than merely absent.
                     * AssertJ's rootCause() walks to the DEEPEST cause, so it
                     * resolved to that IllegalArgumentException ("missing
                     * scheme or host") and never saw the actionable message at
                     * all.
                     *
                     * Asserting on the rendered stack trace covers every link
                     * in the chain, so it stays correct whether or not the
                     * production code chooses to wrap - which is a property of
                     * the diagnostics, not of the exception nesting.
                     */
                    assertThat(context).getFailure()
                            .hasStackTraceContaining(IllegalStateException.class.getName())
                            .hasStackTraceContaining("OLLAMA_BASE_URL");
                });
    }

    @Test
    @DisplayName("the startup failure is ACTIONABLE — it shows the expected form")
    void failureMessageIsActionable() {
        // A fail-fast message is written to stdout on a crashing boot, which is
        // often all an operator sees. "IllegalStateException" is a puzzle; a
        // message showing http://localhost:11434 is a one-minute fix.
        runner().withPropertyValues(
                        "vakilconnect.ai.provider=ollama",
                        "vakilconnect.ai.base-url=not-a-url")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("http://localhost:11434")
                            // The offending value is echoed too - it is our own
                            // configuration, not user data, and seeing what was
                            // actually read is half the diagnosis.
                            .hasStackTraceContaining("not-a-url");
                });
    }

    @Test
    @DisplayName("no property named like a credential is needed by either provider")
    void noCredentialPropertyIsConsumed() {
        /*
         * Guards the requirement from the configuration side rather than the
         * record side (AiPropertiesTest covers that). Both providers are wired
         * from exactly the properties listed in runner() - if someone
         * reintroduced a required key, one of these contexts would fail.
         */
        runner().withPropertyValues("vakilconnect.ai.provider=stub")
                .run(context -> assertThat(context).hasNotFailed());
        runner().withPropertyValues("vakilconnect.ai.provider=ollama")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
