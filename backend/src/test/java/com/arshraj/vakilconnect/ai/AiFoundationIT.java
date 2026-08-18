package com.arshraj.vakilconnect.ai;

import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AI foundation inside the REAL application context.
 *
 * Everything else in this package uses ApplicationContextRunner, which proves
 * the beans work in a context somebody assembled by hand. This proves they work
 * in the one the application actually starts - with component scanning, the full
 * property chain of application.yaml overlaid by application-test.yaml, and
 * every other bean in the system present.
 *
 * It is also the regression test for the risk AI-0 most plausibly introduces:
 * that adding a new @ConfigurationProperties class and two new conditional
 * @Components breaks context startup for the several hundred existing tests that
 * share this context. If that happened, this class fails first and says why.
 */
@DisplayName("AI foundation wiring")
class AiFoundationIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private AiProperties aiProperties;

    @Test
    @DisplayName("exactly one LlmClient is resolvable by the interface")
    void llmClientResolves() {
        // By the INTERFACE, which is the whole point of AI-0: every future
        // caller injects LlmClient and none of them may name a provider.
        assertNotNull(llmClient);
        assertEquals(1, context.getBeanNamesForType(LlmClient.class).length,
                "exactly one provider must ever be active");
    }

    @Test
    @DisplayName("the test suite runs against the stub and CANNOT reach an inference server")
    void suiteUsesTheStub() {
        /*
         * The guard that keeps the build deterministic and independent of what
         * happens to be installed on the machine running it. Both halves
         * matter: the active client is the stub, AND the Ollama adapter does
         * not exist in this context at all - so there is nothing for a stray
         * autowire to find, and a developer with Ollama running on
         * localhost:11434 gets exactly the same result as CI, which has none.
         */
        assertEquals(AiProperties.STUB, llmClient.providerName());
        assertEquals(0, context.getBeanNamesForType(OllamaLlmClient.class).length);
        assertEquals(AiProperties.STUB, aiProperties.provider());
    }

    @Test
    @DisplayName("no HTTP client is built for AI in the test context")
    void noAiHttpClientExists() {
        // AiConfig carries the same condition as the adapter, so under the stub
        // there is no configured RestClient for AI at all.
        assertEquals(0, context.getBeanNamesForType(AiConfig.class).length);
    }

    @Test
    @DisplayName("properties bind from the real yaml chain")
    void propertiesBindFromYaml() {
        // application.yaml supplies the defaults; application-test.yaml pins the
        // provider. A missing key would bind null and fail @NotNull at startup,
        // so reaching these assertions is itself most of the proof.
        assertNotNull(aiProperties.baseUrl());
        assertNotNull(aiProperties.model());
        assertNotNull(aiProperties.temperature());
        assertNotNull(aiProperties.maxOutputTokens());
        assertNotNull(aiProperties.connectTimeout());
        assertNotNull(aiProperties.readTimeout());
    }

    @Test
    @DisplayName("the whole application starts with NO AI credential configured")
    void startsWithoutAnyAiCredential() {
        /*
         * The requirement, asserted against the real running context rather
         * than a hand-built one: VakilConnect boots, wires an LlmClient and is
         * fully functional with no API key, no billing account and no paid
         * service anywhere in its configuration.
         *
         * AiPropertiesTest proves the record declares no credential component.
         * This proves the assembled application needs none either.
         */
        assertTrue(context.getEnvironment()
                .getProperty("vakilconnect.ai.api-key", "").isEmpty(),
                "no AI credential property should exist or be set");
        assertNotNull(llmClient);
    }

    @Test
    @DisplayName("a completion works end to end through the interface")
    void completionWorks() {
        LlmResponse response = llmClient.complete(LlmRequest.of("smoke-test", "hello"));

        assertNotNull(response.text());
        assertFalse(response.text().isBlank());
        assertEquals(StubLlmClient.STUB_MODEL, response.model());
    }
}
