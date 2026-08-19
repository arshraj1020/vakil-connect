package com.arshraj.vakilconnect.ai.embedding;

import com.arshraj.vakilconnect.ai.AiMetrics;
import com.arshraj.vakilconnect.ai.AiProperties;
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
 * Which EmbeddingClient materialises, under which properties.
 *
 * The same shape as AI-0's LlmClientSelectionTest, and for the same reasons.
 * The two properties this enforces are the ones that keep AI-2 free: the
 * provider needing nothing installed is the DEFAULT, and wiring the real one
 * requires NO CREDENTIAL of any kind.
 */
@DisplayName("EmbeddingClient provider selection")
class EmbeddingProviderSelectionTest {

    /**
     * @TestConfiguration so component scanning cannot pull it into the real
     * @SpringBootTest contexts - a plain @Configuration in a scanned test
     * package leaks its beans into every integration test in the suite.
     */
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

        /**
         * OllamaEmbeddingClient injects the `ollamaRestClient` bean that
         * AiConfig declares under provider=ollama. Supplied directly here so
         * this test does not also have to satisfy AiConfig's own condition -
         * the question under test is EMBEDDING provider selection, not chat.
         */
        @Bean("ollamaRestClient")
        RestClient ollamaRestClient() {
            return RestClient.builder().build();
        }
    }

    @TestConfiguration
    @EnableConfigurationProperties({ AiEmbeddingProperties.class, AiProperties.class })
    static class BoundConfig extends MetricsConfig {
    }

    /**
     * Both clients are OFFERED; their own @ConditionalOnProperty decides.
     *
     * @Import, not withBean(): withBean registers UNCONDITIONALLY and would
     * bypass the very conditions under test, making every assertion here pass
     * vacuously.
     */
    @TestConfiguration
    @Import({ StubEmbeddingClient.class, OllamaEmbeddingClient.class })
    static class ProviderConfig {
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(BoundConfig.class, ProviderConfig.class)
                .withPropertyValues(
                        "vakilconnect.ai.provider=ollama",
                        "vakilconnect.ai.base-url=http://localhost:11434",
                        "vakilconnect.ai.model=llama3.2",
                        "vakilconnect.ai.temperature=0.2",
                        "vakilconnect.ai.max-output-tokens=1024",
                        "vakilconnect.ai.connect-timeout=PT5S",
                        "vakilconnect.ai.read-timeout=PT120S",
                        "vakilconnect.ai.embedding.model=nomic-embed-text",
                        "vakilconnect.ai.embedding.dimension=768");
    }

    @Test
    @DisplayName("provider=stub selects the stub and no Ollama bean exists")
    void stubSelected() {
        runner().withPropertyValues("vakilconnect.ai.embedding.provider=stub").run(context -> {
            assertThat(context).hasSingleBean(EmbeddingClient.class);
            assertThat(context).hasSingleBean(StubEmbeddingClient.class);
            assertThat(context).doesNotHaveBean(OllamaEmbeddingClient.class);

            assertThat(context.getBean(EmbeddingClient.class).providerName())
                    .isEqualTo(AiEmbeddingProperties.STUB);
        });
    }

    @Test
    @DisplayName("provider=ollama wires up with NO API KEY of any kind")
    void ollamaSelectedWithoutCredential() {
        /*
         * The requirement that defines this whole project's AI layer. The only
         * properties set are a URL, a model and a dimension - no key, no token,
         * no secret - and the context starts anyway.
         */
        runner().withPropertyValues("vakilconnect.ai.embedding.provider=ollama").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(EmbeddingClient.class);
            assertThat(context).hasSingleBean(OllamaEmbeddingClient.class);
            assertThat(context).doesNotHaveBean(StubEmbeddingClient.class);

            assertThat(context.getBean(EmbeddingClient.class).providerName())
                    .isEqualTo(AiEmbeddingProperties.OLLAMA);
        });
    }

    @Test
    @DisplayName("wiring the Ollama client does NOT contact Ollama")
    void ollamaWiringDoesNotProbeTheServer() {
        /*
         * Asserted so nobody "improves" the constructor into a startup health
         * check. Ollama is a tool developers start and stop freely, and CI has
         * none - a backend that refused to boot without it would be hostile.
         *
         * The port here is one nothing is listening on. If the client probed at
         * construction, this context would fail. It starts.
         */
        runner().withPropertyValues(
                        "vakilconnect.ai.embedding.provider=ollama",
                        "vakilconnect.ai.base-url=http://127.0.0.1:1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OllamaEmbeddingClient.class);
                });
    }

    @Test
    @DisplayName("an unset embedding provider falls back to the stub, never to Ollama")
    void defaultsToStub() {
        // matchIfMissing on StubEmbeddingClient: absent configuration yields the
        // provider that needs no server running and no model pulled.
        new ApplicationContextRunner()
                .withUserConfiguration(MetricsConfig.class, ProviderConfig.class)
                .withBean(AiEmbeddingProperties.class,
                        () -> new AiEmbeddingProperties(
                                AiEmbeddingProperties.STUB, "nomic-embed-text", 768))
                .run(context -> {
                    assertThat(context).hasSingleBean(StubEmbeddingClient.class);
                    assertThat(context).doesNotHaveBean(OllamaEmbeddingClient.class);
                });
    }

    @Test
    @DisplayName("the embedding model is configurable")
    void modelIsConfigurable() {
        runner().withPropertyValues(
                        "vakilconnect.ai.embedding.provider=stub",
                        "vakilconnect.ai.embedding.model=mxbai-embed-large")
                .run(context -> assertThat(
                        context.getBean(AiEmbeddingProperties.class).model())
                        .isEqualTo("mxbai-embed-large"));
    }

    @Test
    @DisplayName("a non-positive dimension fails validation at startup")
    void dimensionIsValidated() {
        runner().withPropertyValues(
                        "vakilconnect.ai.embedding.provider=stub",
                        "vakilconnect.ai.embedding.dimension=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("a blank embedding provider or model fails validation")
    void blankValuesAreRejected() {
        runner().withPropertyValues("vakilconnect.ai.embedding.provider=")
                .run(context -> assertThat(context).hasFailed());
        runner().withPropertyValues(
                        "vakilconnect.ai.embedding.provider=stub",
                        "vakilconnect.ai.embedding.model=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("AiEmbeddingProperties declares NO credential component")
    void declaresNoCredentialComponent() {
        /*
         * The same guard AiPropertiesTest applies to the chat properties, and
         * for the same reason: a record prints every component from its
         * generated toString(), so a credential added here without a redacting
         * override would appear in any log line that formatted this object.
         *
         * Bare "token" and "key" are excluded from the terms for the reason
         * AiPropertiesTest documents - in an AI codebase both have common,
         * legitimate, non-credential meanings.
         */
        for (var component : AiEmbeddingProperties.class.getRecordComponents()) {
            String name = component.getName().toLowerCase(java.util.Locale.ROOT);

            assertThat(name)
                    .as("AiEmbeddingProperties.%s looks like a credential; local "
                            + "inference must never need one", component.getName())
                    .doesNotContain("secret")
                    .doesNotContain("password")
                    .doesNotContain("apikey")
                    .doesNotContain("credential");
        }
    }
}
