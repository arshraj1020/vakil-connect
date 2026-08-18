package com.arshraj.vakilconnect.email;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The Resend adapter, exercised WITHOUT EVER CONTACTING RESEND.
 *
 * MockRestServiceServer is bound to the RestClient.Builder the sender is
 * constructed with, so every request is intercepted in-process. No network, no
 * API key, no real email, no new test dependency - MockRestServiceServer ships
 * in spring-test, which is already on the classpath. That is why WireMock and
 * MockWebServer were not added.
 *
 * Retry is exercised through a real Spring context (AnnotationConfigApplicationContext
 * + @EnableRetry) rather than by calling the class directly: @Retryable is
 * proxy-based, so a bare `new ResendEmailSender(...)` would retry zero times
 * and the test would prove nothing.
 */
@DisplayName("ResendEmailSender")
class ResendEmailSenderTest {

    private static final String API_KEY = "re_test_key_not_real";
    private static final String SEND_URL = "https://api.resend.com/emails";

    private AnnotationConfigApplicationContext context;
    private MockRestServiceServer server;
    private MeterRegistry registry;

    /**
     * Typed as the INTERFACE, not as ResendEmailSender - and that is forced by
     * how retry works, not a style preference.
     *
     * @EnableRetry defaults to proxyTargetClass=false, so Spring wraps the
     * @Retryable bean in a JDK DYNAMIC PROXY. A JDK proxy implements only the
     * target's interfaces, so the object in the context is an EmailService but
     * is NOT a ResendEmailSender - which is exactly why getBean(
     * ResendEmailSender.class) raised NoSuchBeanDefinitionException.
     *
     * Resolving by interface is also what production does: EmailDispatchListener
     * injects EmailService. Forcing CGLIB with proxyTargetClass=true would have
     * made the class-typed lookup work, but it would have made the test exercise
     * a proxying strategy the application never uses.
     */
    private EmailService sender;

    /**
     * The builder the mock server is bound to, and the one the bean is built
     * from - they MUST be the same instance or the sender would make real
     * network calls.
     *
     * Static so the @Configuration below can reach it, but REASSIGNED in
     * @BeforeEach rather than final: MockRestServiceServer mutates the builder
     * it binds to, so reusing one instance across tests would carry a previous
     * test's expectations into the next one.
     */
    private static RestClient.Builder builderHolder;

    @Configuration
    @EnableRetry
    static class TestConfig {

        @Bean
        RestClient.Builder restClientBuilder() {
            return builderHolder;
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        EmailMetrics emailMetrics(MeterRegistry registry) {
            return new EmailMetrics(registry);
        }

        @Bean
        EmailProperties emailProperties() {
            return new EmailProperties(
                    EmailProperties.RESEND, API_KEY, "noreply@vakilconnect.test", "VakilConnect");
        }

        @Bean
        ResendEmailSender resendEmailSender(RestClient.Builder builder,
                                            EmailProperties properties,
                                            EmailMetrics metrics) {
            return new ResendEmailSender(builder, properties, metrics);
        }
    }

    @BeforeEach
    void setUp() {
        // Fresh builder per test, then bind BEFORE the context builds the
        // client - so the sender's RestClient carries the mock request factory
        // and no request can escape to api.resend.com.
        builderHolder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builderHolder).build();

        context = new AnnotationConfigApplicationContext(TestConfig.class);
        sender = context.getBean(EmailService.class);
        registry = context.getBean(MeterRegistry.class);
    }

    /**
     * Guards the root cause of the earlier failure, and the premise of every
     * retry assertion below.
     *
     * If @EnableRetry were removed from TestConfig the bean would be a plain
     * ResendEmailSender - no proxy, no retry - and the multi-attempt tests would
     * then fail with a confusing "unexpected request" from the mock server
     * rather than naming the real problem. This states the premise directly.
     */
    @Test
    @DisplayName("the bean under test is a retry proxy, not the raw class")
    void beanIsRetryProxy() {
        assertTrue(AopUtils.isAopProxy(sender),
                "expected a Spring AOP proxy so @Retryable is active, got: "
                        + sender.getClass());
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    private static EmailMessage message() {
        return new EmailMessage("client@example.com", "Verify your email",
                "<p>hello</p>", "hello", "verification");
    }

    private double count(String outcome) {
        var counter = registry.find(EmailMetrics.SEND_COUNTER)
                .tag("type", "verification")
                .tag("outcome", outcome)
                .counter();
        return counter == null ? 0d : counter.count();
    }

    // ----------------------------------------------------------- happy path

    @Test
    @DisplayName("POSTs to /emails with a Bearer token and the documented payload")
    void sendsCorrectRequest() {
        server.expect(ExpectedCount.once(), requestTo(SEND_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                // The key must travel as a Bearer credential, and nowhere else.
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("VakilConnect <noreply@vakilconnect.test>"))
                .andExpect(jsonPath("$.to[0]").value("client@example.com"))
                .andExpect(jsonPath("$.subject").value("Verify your email"))
                .andExpect(jsonPath("$.html").value("<p>hello</p>"))
                .andExpect(jsonPath("$.text").value("hello"))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        sender.send(message());

        server.verify();
        assertEquals(1d, count(EmailMetrics.OUTCOME_SENT));
    }

    @Test
    @DisplayName("records send duration")
    void recordsDuration() {
        server.expect(requestTo(SEND_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        sender.send(message());

        assertNotNull(registry.find(EmailMetrics.SEND_TIMER).tag("type", "verification").timer());
    }

    // --------------------------------------------------------------- retry

    @Test
    @DisplayName("5xx is retried to exactly 3 attempts, then @Recover")
    void retriesServerErrors() {
        server.expect(ExpectedCount.times(3), requestTo(SEND_URL))
                .andRespond(withServerError());

        // @Recover swallows: the caller is an async listener with nobody to
        // propagate to. The counter is what makes the failure observable.
        sender.send(message());

        server.verify();
        assertEquals(1d, count(EmailMetrics.OUTCOME_FAILURE));
        assertEquals(0d, count(EmailMetrics.OUTCOME_SENT));
    }

    @Test
    @DisplayName("429 is retried — rate limiting is exactly what backoff is for")
    void retriesRateLimit() {
        server.expect(ExpectedCount.times(3), requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        sender.send(message());

        server.verify();
        assertEquals(1d, count(EmailMetrics.OUTCOME_FAILURE));
    }

    @Test
    @DisplayName("a retried call that later succeeds is counted as sent, not failed")
    void succeedsOnSecondAttempt() {
        server.expect(ExpectedCount.once(), requestTo(SEND_URL))
                .andRespond(withServerError());
        server.expect(ExpectedCount.once(), requestTo(SEND_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        sender.send(message());

        server.verify();
        assertEquals(1d, count(EmailMetrics.OUTCOME_SENT));
        assertEquals(0d, count(EmailMetrics.OUTCOME_FAILURE));
    }

    // ---------------------------------------------------------- no retry

    /*
     * WHY THESE ASSERT AN OUTCOME RATHER THAN A THROW.
     *
     * PermanentEmailSendException extends EmailSendException, and @Recover is
     * declared on EmailSendException - Spring Retry matches recovery methods by
     * ASSIGNABILITY, so a permanent failure is recovered too. The retry policy
     * refuses the retry (noRetryFor), RetryTemplate treats that as exhausted,
     * the recovery callback runs, and send() returns normally.
     *
     * That is the intended production behaviour, not a defect: the caller is an
     * async listener on a pool thread whose transaction committed long ago, so
     * there is nobody to propagate to. The counter is what makes the failure
     * visible.
     *
     * "Not retried" is therefore proved by the ATTEMPT COUNT, which is a
     * stronger claim than "something was thrown" - a throw would be consistent
     * with three attempts too. The exception TYPE is asserted separately below
     * against an unproxied sender.
     */

    @Test
    @DisplayName("4xx is NOT retried — exactly one attempt, counted as failure")
    void doesNotRetryClientErrors() {
        // A malformed address or an unverified sending domain fails identically
        // on every attempt; retrying only burns quota and delays the signal.
        server.expect(ExpectedCount.once(), requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        sender.send(message());

        // once() + verify() IS the no-retry assertion: a second attempt would
        // fail here as an unexpected request.
        server.verify();
        assertEquals(1d, count(EmailMetrics.OUTCOME_FAILURE));
        assertEquals(0d, count(EmailMetrics.OUTCOME_SENT));
    }

    @Test
    @DisplayName("401 is NOT retried — a bad API key will not fix itself")
    void doesNotRetryUnauthorized() {
        server.expect(ExpectedCount.once(), requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        sender.send(message());

        server.verify();
        assertEquals(1d, count(EmailMetrics.OUTCOME_FAILURE));
        assertEquals(0d, count(EmailMetrics.OUTCOME_SENT));
    }

    // -------------------------------------------------- exception mapping
    //
    // Asserted against a RAW, UNPROXIED sender. Without the retry proxy there
    // is no @Recover to absorb the exception, so the mapping from HTTP status
    // to exception TYPE is directly observable - coverage that would otherwise
    // be lost now that the proxied tests assert an outcome instead of a throw.

    /** A sender with no Spring proxy: exceptions propagate to the caller. */
    private ResendEmailSender unproxiedSender(RestClient.Builder builder) {
        return new ResendEmailSender(
                builder,
                new EmailProperties(EmailProperties.RESEND, API_KEY,
                        "noreply@vakilconnect.test", "VakilConnect"),
                new EmailMetrics(new SimpleMeterRegistry()));
    }

    @Test
    @DisplayName("4xx maps to PermanentEmailSendException")
    void mapsClientErrorToPermanentException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer raw = MockRestServiceServer.bindTo(builder).build();
        raw.expect(ExpectedCount.once(), requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        ResendEmailSender direct = unproxiedSender(builder);

        assertThrows(PermanentEmailSendException.class, () -> direct.send(message()));
        raw.verify();
    }

    @Test
    @DisplayName("5xx maps to the retryable EmailSendException, not the permanent one")
    void mapsServerErrorToTransientException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer raw = MockRestServiceServer.bindTo(builder).build();
        raw.expect(ExpectedCount.once(), requestTo(SEND_URL))
                .andRespond(withServerError());

        ResendEmailSender direct = unproxiedSender(builder);

        EmailSendException thrown = assertThrows(EmailSendException.class,
                () -> direct.send(message()));

        // The distinction the retry policy keys on. If 5xx were mapped to the
        // permanent subclass, noRetryFor would silently disable retry entirely.
        assertFalse(thrown instanceof PermanentEmailSendException,
                "5xx must be transient so @Retryable can act on it");
        assertTrue(thrown.isRetryable());
        raw.verify();
    }

    // ------------------------------------------------- status classification

    @Test
    @DisplayName("retryable-status classification")
    void statusClassification() {
        assertEquals(true, ResendEmailSender.isRetryableStatus(500));
        assertEquals(true, ResendEmailSender.isRetryableStatus(503));
        assertEquals(true, ResendEmailSender.isRetryableStatus(429));
        assertEquals(false, ResendEmailSender.isRetryableStatus(400));
        assertEquals(false, ResendEmailSender.isRetryableStatus(401));
        assertEquals(false, ResendEmailSender.isRetryableStatus(422));
    }
}
