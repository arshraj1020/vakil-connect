package com.arshraj.vakilconnect.email;

import com.arshraj.vakilconnect.email.event.SendEmailRequestedEvent;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AFTER_COMMIT dispatch semantics against a real transaction manager.
 *
 * THE SYNCHRONOUS EXECUTOR IS NOT A SHORTCUT. Overriding `emailTaskExecutor`
 * with SyncTaskExecutor makes @Async run inline, so an assertion immediately
 * after commit observes the send deterministically. With the real pool these
 * tests would race the worker thread: green locally, intermittently red in CI -
 * the worst failure mode available. Bean overriding is enabled for the test
 * profile only (see application-test.yaml).
 *
 * NO TEST HERE EVER REACHES A PROVIDER: the suite runs with
 * vakilconnect.email.provider=console, and this class additionally substitutes
 * a recording double.
 */
@DisplayName("Email dispatch (AFTER_COMMIT)")
@Import({
        EmailDispatchIT.SyncExecutorConfig.class,
        EmailDispatchIT.RecordingEmailSender.class,
        EmailDispatchIT.TransactionalPublisher.class
})
class EmailDispatchIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class SyncExecutorConfig {
        /** Same bean name as AsyncConfig's, so @Async("emailTaskExecutor") resolves to this. */
        @Bean("emailTaskExecutor")
        Executor emailTaskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    /**
     * Captures messages instead of sending them.
     *
     * The only legitimate place a rendered email body is readable after
     * dispatch - which is exactly why it lives in test sources and nowhere near
     * the main source set.
     *
     * @Primary because the test profile sets provider=console, so
     * ConsoleEmailSender is also present; without this, EmailDispatchListener's
     * constructor injection would fail on two EmailService candidates.
     *
     * DELIBERATELY NOT @Component. It is registered by the @Import above, which
     * is enough - and @Component would be actively harmful: this class sits in
     * `com.arshraj.vakilconnect.email` in TEST sources, which @SpringBootApplication's
     * component scan covers with test-classes on the same classpath. Boot's
     * TypeExcludeFilter excludes @TestConfiguration but NOT @Component, so a
     * @Component here is scanned into EVERY @SpringBootTest context in the suite.
     *
     * That is not hypothetical: with this annotation present, any other test
     * contributing its own @Primary EmailService produced TWO primary
     * candidates and the context failed to start.
     */
    @Primary
    static class RecordingEmailSender implements EmailService {

        private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
        private volatile boolean explode = false;

        @Override
        public void send(EmailMessage message) {
            if (explode) {
                throw new EmailSendException("simulated transport failure");
            }
            sent.add(message);
        }

        void reset() {
            sent.clear();
            explode = false;
        }
    }

    @Autowired
    private RecordingEmailSender recorder;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private TransactionalPublisher transactionalPublisher;

    /**
     * Publishes inside a real transaction so AFTER_COMMIT can fire.
     *
     * Not @Component, for the same reason as RecordingEmailSender above: the
     * @Import registers it, and @Component would leak it into every other
     * @SpringBootTest context in the suite.
     */
    static class TransactionalPublisher {

        private final ApplicationEventPublisher publisher;

        TransactionalPublisher(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public void publishAndCommit(EmailMessage message) {
            publisher.publishEvent(new SendEmailRequestedEvent(message));
        }

        @Transactional
        public void publishAndRollback(EmailMessage message) {
            publisher.publishEvent(new SendEmailRequestedEvent(message));
            throw new IllegalStateException("forced rollback");
        }
    }

    private static EmailMessage message(String tag) {
        return new EmailMessage("client@example.com", "Subject", "<p>body</p>", "body", tag);
    }

    private double count(String type, String outcome) {
        var counter = registry.find(EmailMetrics.SEND_COUNTER)
                .tag("type", type).tag("outcome", outcome).counter();
        return counter == null ? 0d : counter.count();
    }

    @BeforeEach
    void resetRecorder() {
        recorder.reset();
    }

    @Nested
    @DisplayName("commit semantics")
    class CommitSemantics {

        @Test
        @DisplayName("a committed transaction sends the email")
        void committedTransactionSends() {
            transactionalPublisher.publishAndCommit(message("commit-ok"));

            assertEquals(1, recorder.sent.size());
            assertEquals("commit-ok", recorder.sent.get(0).tag());
        }

        @Test
        @DisplayName("a ROLLED-BACK transaction sends NOTHING")
        void rolledBackTransactionSendsNothing() {
            // The reason AFTER_COMMIT exists. Sending inside the transaction
            // would leave a live verification link for a user row that was
            // never persisted - and an email cannot be un-sent.
            assertThrows(IllegalStateException.class,
                    () -> transactionalPublisher.publishAndRollback(message("rollback")));

            assertTrue(recorder.sent.isEmpty(),
                    "a rolled-back transaction must not send email");
        }

        @Test
        @DisplayName("publishing OUTSIDE a transaction sends nothing (and is logged as an error)")
        void outsideTransactionSendsNothing() {
            /*
             * Documents the silent-failure mode rather than hiding it: a
             * @TransactionalEventListener bound to AFTER_COMMIT does nothing at
             * all when no transaction is active. EmailDispatchListener's
             * warnIfPublishedOutsideTransaction guard logs an ERROR in exactly
             * this case so it is discoverable rather than invisible.
             */
            publisher.publishEvent(new SendEmailRequestedEvent(message("no-tx")));

            assertTrue(recorder.sent.isEmpty(),
                    "AFTER_COMMIT cannot fire without a transaction");
        }
    }

    @Nested
    @DisplayName("failure handling")
    class FailureHandling {

        @Test
        @DisplayName("a sender failure is counted and never propagates to the caller")
        void senderFailureIsSwallowedAndCounted() {
            recorder.explode = true;

            // Must not throw: this runs on a pool thread after the response was
            // already sent, so there is nobody to propagate to.
            transactionalPublisher.publishAndCommit(message("boom"));

            assertEquals(1d, count("boom", EmailMetrics.OUTCOME_FAILURE));
            assertTrue(recorder.sent.isEmpty());
        }
    }
}
