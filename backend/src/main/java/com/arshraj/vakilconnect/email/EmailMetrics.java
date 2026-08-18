package com.arshraj.vakilconnect.email;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Delivery counters for outbound email.
 *
 * MeterRegistry DIRECTLY, NOT MeterBinder. ReferenceMigrationMetrics is a
 * MeterBinder because it SURFACES values that already exist elsewhere
 * (FunctionCounter/Gauge over a live object). Email outcomes are events - there
 * is no pre-existing number to bind, you increment at the moment of success or
 * failure - so a MeterBinder cannot express them.
 *
 * NAME USES DOTS. Micrometer translates `vakilconnect.email.send` into
 * `vakilconnect_email_send_total` at the Prometheus scrape. Registering the
 * underscored name in Java would produce a doubled suffix and break the
 * convention every other meter in this codebase follows.
 *
 * TAG SAFETY. `type` is the message tag - a fixed, developer-chosen label such
 * as "verification". It must NEVER be an email address, a user id or anything
 * user-supplied: tag values become time series, so an unbounded tag is a
 * cardinality explosion, and a personal one leaks PII into a metrics store with
 * a completely different access-control model from the database.
 */
@Component
public class EmailMetrics {

    static final String SEND_COUNTER = "vakilconnect.email.send";
    static final String SEND_TIMER = "vakilconnect.email.send.duration";

    static final String OUTCOME_SENT = "sent";
    static final String OUTCOME_FAILURE = "failure";
    static final String OUTCOME_REJECTED = "rejected";

    /**
     * Tag value for a rejection with no message context.
     *
     * PACKAGE-PRIVATE ON PURPOSE. The literal is how this class chooses to
     * label an unattributable rejection - an internal detail of the metric
     * schema, not part of the API. Callers outside this package use
     * {@link #recordRejected()} instead, so the tag vocabulary stays owned here
     * and can change without touching them.
     */
    static final String TYPE_UNKNOWN = "unknown";

    private final MeterRegistry registry;

    public EmailMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Delivered to the provider without error. */
    public void recordSent(String type) {
        increment(type, OUTCOME_SENT);
    }

    /** Every attempt exhausted, or a permanent failure. The email is lost. */
    public void recordFailure(String type) {
        increment(type, OUTCOME_FAILURE);
    }

    /**
     * The executor queue was full and the task was dropped before it ran.
     *
     * Distinct from `failure` on purpose: failure means the provider rejected
     * us, rejected means WE ran out of capacity. They call for completely
     * different remedies, and collapsing them would hide a saturation problem
     * behind what looks like a provider outage.
     */
    public void recordRejected(String type) {
        increment(type, OUTCOME_REJECTED);
    }

    /**
     * A rejection whose message is unknown.
     *
     * The executor's RejectedExecutionHandler receives only a Runnable, so it
     * genuinely cannot say which email was dropped. This overload exists so
     * that caller does not have to reach for the tag literal - it names the
     * situation ("rejected, no context") rather than exposing how that is
     * encoded, which keeps the metric vocabulary owned by this class.
     */
    public void recordRejected() {
        increment(TYPE_UNKNOWN, OUTCOME_REJECTED);
    }

    /** Provider latency, tagged by message type. */
    public void recordDuration(String type, Duration duration) {
        Timer.builder(SEND_TIMER)
                .tag("type", type)
                .register(registry)
                .record(duration);
    }

    private void increment(String type, String outcome) {
        Counter.builder(SEND_COUNTER)
                .tag("type", type)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }
}
