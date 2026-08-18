package com.arshraj.vakilconnect.config;

import com.arshraj.vakilconnect.email.EmailMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async execution and retry for outbound email.
 *
 * @EnableRetry IS NOT OPTIONAL. Without it @Retryable is completely inert -
 * the annotation is ignored, no proxy is created, and a single failed send is
 * simply lost. It fails silently, which is why it is called out here rather
 * than assumed.
 */
@Configuration
@EnableAsync
@EnableRetry
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Bounded pool for email dispatch. Core 2, max 5, queue 100.
     *
     * BOUNDED IS THE POINT. Spring's default for @Async is
     * SimpleAsyncTaskExecutor, which spawns a NEW THREAD PER TASK with no
     * limit - under a provider slowdown that is an unbounded thread leak that
     * takes the whole JVM down. Every number here is a ceiling.
     *
     * Dedicated to email rather than shared: a saturated email queue must not
     * be able to starve any other async work added later.
     */
    @Bean("emailTaskExecutor")
    public Executor emailTaskExecutor(EmailMetrics metrics) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");

        /*
         * COUNTING REJECTION HANDLER, NOT AbortPolicy.
         *
         * The requirement is that a dropped task is never invisible. Raw
         * AbortPolicy throws RejectedExecutionException on the SUBMITTING
         * thread - which here is the transaction-synchronisation callback,
         * running after commit and after the HTTP response has already gone
         * out. Nothing would increment a counter, and the exception would
         * surface only as a framework log line at some unrelated level.
         *
         * So: count it, log it at WARN, and return without throwing. The task
         * is still dropped - that is the accepted behaviour under saturation,
         * because the alternative (CallerRunsPolicy) would consume request
         * threads sending email and turn a degraded provider into a degraded
         * application. But it is dropped VISIBLY.
         *
         * LIMITATION, STATED: the handler receives only a Runnable, so it
         * cannot know which message was dropped. The no-arg recordRejected()
         * names that situation; how EmailMetrics tags it is that class's
         * business, not this one's. The counter still answers the question that
         * matters - "are we shedding email?" - and the fix for saturation is
         * capacity, not per-type attribution.
         */
        executor.setRejectedExecutionHandler((runnable, threadPoolExecutor) -> {
            metrics.recordRejected();
            log.warn("Email task rejected: executor saturated "
                            + "(active={}, queued={}, poolSize={}). The email was DROPPED.",
                    threadPoolExecutor.getActiveCount(),
                    threadPoolExecutor.getQueue().size(),
                    threadPoolExecutor.getPoolSize());
        });

        /*
         * Let in-flight sends finish on shutdown, but do not hang a deploy
         * behind a slow provider.
         */
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);

        executor.initialize();
        return executor;
    }
}
