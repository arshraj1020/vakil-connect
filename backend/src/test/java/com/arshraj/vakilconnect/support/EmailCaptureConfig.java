package com.arshraj.vakilconnect.support;

import com.arshraj.vakilconnect.email.EmailMessage;
import com.arshraj.vakilconnect.email.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captures outbound email so Phase 4 tests can read the verification link.
 *
 * THE ONLY LEGITIMATE PLACE A RAW TOKEN IS READABLE after issuance - which is
 * exactly why this lives in test sources. Production never has a way to recover
 * the raw value: only its HMAC is stored.
 *
 * THE SYNCHRONOUS EXECUTOR IS NOT A SHORTCUT. `emailTaskExecutor` is replaced
 * with SyncTaskExecutor so @Async runs inline and an assertion immediately after
 * commit observes the dispatch deterministically. With the real pool these tests
 * would race the worker thread - green locally, intermittently red in CI. Bean
 * overriding is enabled for the test profile only.
 */
@TestConfiguration
public class EmailCaptureConfig {

    /** Same bean name as AsyncConfig's, so @Async("emailTaskExecutor") resolves here. */
    @Bean("emailTaskExecutor")
    public Executor emailTaskExecutor() {
        return new SyncTaskExecutor();
    }

    /**
     * @Primary because the test profile sets provider=console, so
     * ConsoleEmailSender is also present; without this, EmailDispatchListener
     * would fail on two EmailService candidates.
     */
    @Bean
    @Primary
    public RecordingEmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }

    /** Records messages instead of sending them. */
    public static class RecordingEmailSender implements EmailService {

        /** Matches the token in a verification link, whichever body carries it. */
        private static final Pattern TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

        private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            sent.add(message);
        }

        public List<EmailMessage> sent() {
            return sent;
        }

        public int count() {
            return sent.size();
        }

        public void reset() {
            sent.clear();
        }

        public EmailMessage last() {
            if (sent.isEmpty()) {
                throw new AssertionError("no email was captured");
            }
            return sent.get(sent.size() - 1);
        }

        /**
         * Pulls the raw token out of the most recent verification link.
         *
         * Reads the TEXT body: it carries the bare URL, whereas the HTML body
         * wraps it in an anchor. Failing loudly here beats returning null and
         * producing a confusing NPE three assertions later.
         */
        public String lastToken() {
            EmailMessage message = last();
            String body = message.text() != null ? message.text() : message.html();

            Matcher matcher = TOKEN.matcher(body);
            if (!matcher.find()) {
                throw new AssertionError("no ?token= found in the captured email body");
            }
            return matcher.group(1);
        }
    }
}
