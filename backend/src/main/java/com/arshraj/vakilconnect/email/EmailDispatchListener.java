package com.arshraj.vakilconnect.email;

import com.arshraj.vakilconnect.email.event.SendEmailRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Turns a committed {@link SendEmailRequestedEvent} into an actual send.
 *
 * WHY AFTER COMMIT. Sending inside the transaction produces two distinct
 * failures:
 *
 *   1. A rollback AFTER a successful send leaves a live verification link for a
 *      user row that no longer exists. The email cannot be un-sent.
 *   2. The provider's HTTP latency is added to the time a Hikari connection is
 *      held, so a slow provider becomes connection-pool exhaustion - a database
 *      outage caused by an email vendor.
 *
 * Binding to AFTER_COMMIT means an email is only ever sent for data that
 * actually persisted.
 *
 * WHY ASYNC. AFTER_COMMIT listeners run synchronously on the committing thread
 * by default, so without @Async the HTTP response would still wait for the
 * provider. The executor is bounded and rejects rather than queues without
 * limit - see AsyncConfig.
 *
 * WHY @Retryable IS NOT HERE. It lives on ResendEmailSender.send(). Both are
 * proxy-based, and stacking them on one method makes the interception order
 * implicit; on separate beans each proxy wraps exactly one concern.
 */
@Component
public class EmailDispatchListener {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchListener.class);

    private final EmailService emailService;
    private final EmailMetrics metrics;

    public EmailDispatchListener(EmailService emailService, EmailMetrics metrics) {
        this.emailService = emailService;
        this.metrics = metrics;
    }

    /**
     * The dispatch path. Runs on {@code emailTaskExecutor} after the publishing
     * transaction commits.
     *
     * SWALLOWS EVERYTHING. This runs on a pool thread long after the HTTP
     * response was sent, so there is nobody to propagate to; an escaping
     * exception would only produce an unhandled-async-exception stack trace.
     * ResendEmailSender's @Recover already counts retry exhaustion, so this
     * catch exists for the failures that bypass it entirely - a bug in the
     * sender, or a permanent failure on the first attempt.
     */
    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSendEmailRequested(SendEmailRequestedEvent event) {
        EmailMessage message = event.message();
        try {
            emailService.send(message);
        } catch (RuntimeException e) {
            metrics.recordFailure(message.tag());
            // Tag and reason only - never the recipient, body or link.
            log.error("Email dispatch failed for {}: {}", message.tag(), e.toString());
        }
    }

    /**
     * GUARD: detects an event published with no transaction in progress.
     *
     * A @TransactionalEventListener bound to AFTER_COMMIT does nothing at all
     * when there is no transaction - it does not throw, it does not warn, the
     * event is simply dropped and the email never arrives. That is the single
     * most likely way this feature breaks silently, and it would break in
     * production while every test that happens to be transactional passes.
     *
     * A plain @EventListener fires in BOTH cases, so checking for an active
     * transaction here distinguishes them exactly: if one is active the real
     * listener above will run at commit and this method stays quiet; if not,
     * nothing will run and this logs an ERROR naming the cause.
     *
     * Deliberately does not send the email itself. Falling back to an immediate
     * send would restore delivery at the cost of the AFTER_COMMIT guarantee -
     * the very property this design exists to provide.
     */
    @EventListener
    public void warnIfPublishedOutsideTransaction(SendEmailRequestedEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            log.error("SendEmailRequestedEvent for {} was published OUTSIDE a transaction; "
                            + "the AFTER_COMMIT listener will not fire and this email will "
                            + "never be sent. The publishing method must be @Transactional.",
                    event.message().tag());
        }
    }
}
