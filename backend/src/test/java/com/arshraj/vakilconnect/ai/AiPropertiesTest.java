package com.arshraj.vakilconnect.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuration binding, base-URL normalisation, and the guard that keeps this
 * record credential-free.
 *
 * ApplicationContextRunner rather than @SpringBootTest: these assertions are
 * about whether a property maps onto a record component and whether validation
 * refuses a bad value. A full Boot context would need Testcontainers and a
 * database for questions that involve neither.
 */
@DisplayName("AiProperties")
class AiPropertiesTest {

    /**
     * @TestConfiguration, NOT @Configuration. This class lives in a package the
     * application's component scan covers, with test-classes on the same
     * classpath, so a plain @Configuration would be pulled into EVERY
     * @SpringBootTest context in the suite. Boot's TypeExcludeFilter skips
     * @TestConfiguration, so it is only used where it is asked for.
     */
    @TestConfiguration
    @EnableConfigurationProperties(AiProperties.class)
    static class BindingConfig {
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(BindingConfig.class);
    }

    /** The values application.yaml supplies, expressed as the runner's input. */
    private ApplicationContextRunner withDefaults() {
        return runner().withPropertyValues(
                "vakilconnect.ai.provider=stub",
                "vakilconnect.ai.base-url=http://localhost:11434",
                "vakilconnect.ai.model=llama3.2",
                "vakilconnect.ai.temperature=0.2",
                "vakilconnect.ai.max-output-tokens=1024",
                "vakilconnect.ai.connect-timeout=PT5S",
                "vakilconnect.ai.read-timeout=PT120S");
    }

    @Test
    @DisplayName("binds every key, including relaxed kebab-case names")
    void bindsAllKeys() {
        withDefaults().run(context -> {
            AiProperties properties = context.getBean(AiProperties.class);

            assertThat(properties.provider()).isEqualTo(AiProperties.STUB);
            assertThat(properties.baseUrl()).isEqualTo("http://localhost:11434");
            assertThat(properties.model()).isEqualTo("llama3.2");
            assertThat(properties.temperature()).isEqualTo(0.2d);
            assertThat(properties.maxOutputTokens()).isEqualTo(1024);

            // max-output-tokens -> maxOutputTokens and base-url -> baseUrl are
            // the relaxed bindings this project relies on everywhere. Asserting
            // them means a typo in the yaml key is caught by a test rather than
            // by a null at runtime.
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(120));
        });
    }

    @Test
    @DisplayName("the read timeout default is generous enough for local inference")
    void readTimeoutSuitsLocalInference() {
        /*
         * Not a style assertion. A 3B model on CPU takes tens of seconds to
         * write a paragraph, and the first call after `ollama serve` also loads
         * the model into RAM. A cloud-tuned 30s value would fail healthy calls
         * and look like a bug in the adapter, so if someone "tidies" this
         * default downwards a test should argue back.
         */
        withDefaults().run(context -> assertThat(
                context.getBean(AiProperties.class).readTimeout())
                .isGreaterThanOrEqualTo(Duration.ofSeconds(60)));
    }

    // ------------------------------------------------- base URL normalisation

    @Test
    @DisplayName("a trailing slash on the base URL is stripped at bind time")
    void trailingSlashIsStripped() {
        // Otherwise the endpoint becomes http://localhost:11434//api/chat, which
        // some proxies reject outright and which makes an exact-URL assertion in
        // a test quietly misleading. A developer copying a URL out of a terminal
        // should not have to think about this.
        withDefaults()
                .withPropertyValues("vakilconnect.ai.base-url=http://localhost:11434/")
                .run(context -> assertThat(context.getBean(AiProperties.class).baseUrl())
                        .isEqualTo("http://localhost:11434"));
    }

    @Test
    @DisplayName("repeated trailing slashes and surrounding whitespace are stripped")
    void normalisationIsThorough() {
        withDefaults()
                .withPropertyValues("vakilconnect.ai.base-url=  http://localhost:11434///  ")
                .run(context -> assertThat(context.getBean(AiProperties.class).baseUrl())
                        .isEqualTo("http://localhost:11434"));
    }

    @Test
    @DisplayName("a base URL of only slashes normalises to blank and is rejected")
    void slashOnlyBaseUrlIsRejected() {
        // It was never a URL. Normalising it to "" and letting @NotBlank refuse
        // it is the correct outcome, and it must not slip through as a valid
        // empty string.
        withDefaults()
                .withPropertyValues("vakilconnect.ai.base-url=///")
                .run(context -> assertThat(context).hasFailed());
    }

    // ------------------------------------------------------------- validation

    @Test
    @DisplayName("a blank provider fails validation at startup")
    void blankProviderIsRejected() {
        // An empty provider would produce a context with NO LlmClient bean, and
        // the failure would surface later as an unsatisfied dependency rather
        // than as the configuration mistake it is.
        withDefaults()
                .withPropertyValues("vakilconnect.ai.provider=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("a blank model fails validation at startup")
    void blankModelIsRejected() {
        withDefaults()
                .withPropertyValues("vakilconnect.ai.model=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("an out-of-range temperature fails validation at startup")
    void temperatureIsBounded() {
        // The provider would reject this at CALL time, which means the first
        // person to discover it is a user, mid request. Validation moves the
        // discovery to startup.
        withDefaults()
                .withPropertyValues("vakilconnect.ai.temperature=5.0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("a non-positive max-output-tokens fails validation at startup")
    void maxOutputTokensMustBePositive() {
        withDefaults()
                .withPropertyValues("vakilconnect.ai.max-output-tokens=0")
                .run(context -> assertThat(context).hasFailed());
    }

    // -------------------------------------------------------------- security

    /**
     * Every component of {@link AiProperties}, each individually reviewed and
     * confirmed to hold no secret.
     *
     * THIS IS AN ALLOWLIST, NOT A DENYLIST, AND THAT IS THE WHOLE DESIGN. A
     * denylist has to guess which names look dangerous, and a guess is exactly
     * what broke the first version of this test: it rejected any name
     * containing "token", which condemned {@code maxOutputTokens} - a token
     * COUNT. In an LLM codebase "token" is domain vocabulary for a unit of
     * text, so the one word most associated with credentials elsewhere is one
     * of the most common innocent words here.
     *
     * An allowlist inverts the burden. It does not need to recognise a
     * credential; it only needs to notice that the record CHANGED, which is
     * mechanical and cannot produce a false positive. A field named
     * {@code apiKey}, {@code gcpCred} or {@code zzz} all fail identically.
     */
    private static final Set<String> REVIEWED_COMPONENTS = Set.of(
            "provider",          // stub or ollama
            "baseUrl",           // http://localhost:11434 - a local address
            "model",             // a public model tag such as llama3.2
            "temperature",       // a number
            "maxOutputTokens",   // a COUNT OF TEXT TOKENS. Not a credential.
            "connectTimeout",    // a Duration
            "readTimeout");      // a Duration

    /**
     * Credential terms that are UNAMBIGUOUS IN THIS CODEBASE.
     *
     * WHY BARE "token" AND BARE "key" ARE DELIBERATELY ABSENT. Both have common,
     * legitimate, non-credential meanings in an AI project - a token is a unit
     * of text (`maxOutputTokens`, `tokenCount`, `promptTokens`), and a key is a
     * map or cache key (`cacheKey`, `keyPrefix`). Including them is what
     * produced the false positive this test was written to remove. The compound
     * forms below have no such second meaning: nothing called `apiKey` or
     * `authToken` is ever anything but a credential.
     *
     * This list is the SECOND net, not the first. {@link #componentSetIsPinned()}
     * catches every change regardless of naming; this one exists so that when a
     * credential is added, the failure message says "this is a credential"
     * rather than merely "something changed".
     */
    private static final Set<String> UNAMBIGUOUS_CREDENTIAL_TERMS = Set.of(
            "secret", "password", "passwd", "credential", "authorization",
            "apikey", "apitoken", "authtoken", "accesstoken", "bearertoken",
            "refreshtoken", "privatekey", "accesskey", "secretkey", "clientsecret");

    @Test
    @DisplayName("the component set is PINNED — any new field must be reviewed")
    void componentSetIsPinned() {
        /*
         * THE MOST IMPORTANT ASSERTION IN THIS CLASS, and the one that encodes
         * the point of choosing local inference: VakilConnect's AI layer needs
         * no API key, no billing account and no paid service.
         *
         * It is also a REGRESSION GUARD with real history. The previous version
         * of this record held a Gemini key and needed a hand-written toString()
         * to stop the generated one printing it - the same hazard
         * EmailProperties still carries. A record prints EVERY component from
         * its generated toString(), so if a hosted provider is added later and
         * someone reintroduces a credential field without that override, a live
         * key would start appearing in any log line, stack trace or debugger
         * frame that formatted this object.
         *
         * The fix when this fires is NOT to paste the new name in and move on:
         *
         *   1. Decide whether the new component holds a secret.
         *   2. If it does, add a redacting toString() to AiProperties FIRST -
         *      copy EmailProperties, which does exactly this for its API key.
         *   3. Only then add the name to REVIEWED_COMPONENTS, which records
         *      that a human looked at it.
         */
        Set<String> actual = Arrays.stream(AiProperties.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(actual)
                .as("AiProperties' shape changed. Every component must be reviewed for "
                        + "whether it holds a secret. If the new one does, add a redacting "
                        + "toString() to AiProperties (see EmailProperties) BEFORE adding "
                        + "the name to REVIEWED_COMPONENTS.")
                .containsExactlyInAnyOrderElementsOf(REVIEWED_COMPONENTS);
    }

    @Test
    @DisplayName("no component is named with an unambiguous credential term")
    void noComponentIsNamedLikeACredential() {
        /*
         * Names are compared after normalising away case and separators, so
         * `apiKey`, `api_key` and `API-KEY` all reduce to `apikey` and are
         * caught identically. Matching is by CONTAINS on compound terms, which
         * is what catches a qualified name like `geminiApiKey`.
         *
         * Verifying the heuristic against the record as it stands today:
         * `maxOutputTokens` normalises to `maxoutputtokens`, which contains
         * none of the terms above - the plural, generic word "tokens" is not in
         * the list precisely because it is not a credential term.
         */
        for (RecordComponent component : AiProperties.class.getRecordComponents()) {
            String normalised = component.getName()
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]", "");

            for (String term : UNAMBIGUOUS_CREDENTIAL_TERMS) {
                assertThat(normalised)
                        .as("AiProperties.%s is named like a credential (matched '%s'). "
                                + "The AI layer is meant to need none. If a paid provider "
                                + "genuinely requires one, add a redacting toString() to "
                                + "AiProperties (see EmailProperties) before this lands.",
                                component.getName(), term)
                        .doesNotContain(term);
            }
        }
    }

    @Test
    @DisplayName("the credential heuristic itself is calibrated")
    void credentialTermsAreCalibrated() {
        /*
         * A test for the test, and it is not ceremony - the original bug here
         * was a miscalibrated heuristic, not a miscalibrated production class.
         * The two assertions below are the exact pair that broke: a real
         * credential name must be caught, and the token COUNT must not be.
         */
        assertThat(matchesCredentialTerm("apiKey")).isTrue();
        assertThat(matchesCredentialTerm("geminiApiKey")).isTrue();
        assertThat(matchesCredentialTerm("clientSecret")).isTrue();
        assertThat(matchesCredentialTerm("authToken")).isTrue();
        assertThat(matchesCredentialTerm("api_key")).isTrue();

        // The false positive this rewrite exists to remove.
        assertThat(matchesCredentialTerm("maxOutputTokens")).isFalse();
        // Other legitimate AI-domain names that a bare "token"/"key" denylist
        // would also have condemned.
        assertThat(matchesCredentialTerm("promptTokens")).isFalse();
        assertThat(matchesCredentialTerm("tokenCount")).isFalse();
        assertThat(matchesCredentialTerm("cacheKey")).isFalse();
    }

    private static boolean matchesCredentialTerm(String componentName) {
        String normalised = componentName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return UNAMBIGUOUS_CREDENTIAL_TERMS.stream().anyMatch(normalised::contains);
    }

    @Test
    @DisplayName("toString() is safe to log because nothing here is secret")
    void toStringIsSafe() {
        // No override exists, and none is needed - there is no credential to
        // hide. This asserts the useful half: the diagnostics are actually
        // present, so logging the bound object is worth doing.
        withDefaults().run(context -> {
            String rendered = context.getBean(AiProperties.class).toString();

            assertThat(rendered).contains("stub");
            assertThat(rendered).contains("llama3.2");
            assertThat(rendered).contains("11434");
        });
    }
}
