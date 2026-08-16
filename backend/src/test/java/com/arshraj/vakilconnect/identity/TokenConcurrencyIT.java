package com.arshraj.vakilconnect.identity;

import com.arshraj.vakilconnect.identity.entity.EmailTokenType;
import com.arshraj.vakilconnect.identity.repository.EmailTokenRepository;
import com.arshraj.vakilconnect.identity.service.TokenHasher;
import com.arshraj.vakilconnect.identity.service.VerificationTokenService;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE most important test in this feature.
 *
 * Two threads present the same token at the same moment. Exactly one must
 * succeed. This is the property the entire single-use guarantee rests on, and
 * it is the one a read-then-write implementation would silently fail.
 *
 * WHY @RepeatedTest(20). A race condition does not fail deterministically. A
 * broken implementation can pass a single run purely because the two threads
 * happened not to interleave. Twenty consecutive runs is not proof either - no
 * test proves the absence of a race - but it converts "we got lucky once" into
 * a signal worth trusting, and it will reliably catch the specific regression
 * of someone replacing the conditional UPDATE with a find-then-save.
 *
 * The correctness itself does not come from this test. It comes from the single
 * conditional UPDATE in EmailTokenRepository, where the predicate and the write
 * are one statement and PostgreSQL serialises the row lock. This test only
 * demonstrates it.
 */
@DisplayName("Token consumption under concurrency")
class TokenConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private VerificationTokenService tokenService;

    @Autowired
    private EmailTokenRepository emailTokenRepository;

    @Autowired
    private TokenHasher tokenHasher;

    @RepeatedTest(20)
    @DisplayName("two simultaneous consumes of one token: exactly one wins")
    void exactlyOneWinner() throws Exception {
        User user = new User();
        user.setFullName("Race Fixture");
        user.setEmail(distinctEmail("race"));
        user.setPasswordHash(passwordEncoderForSupport.encode(DEFAULT_PASSWORD));
        user.setPhoneNumber("9876543210");
        user.setRole(Role.CLIENT);
        user.setActive(true);
        userRepositoryForSupport.save(user);

        String raw = tokenService.issue(user, EmailTokenType.VERIFY_EMAIL);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        // A start latch so both threads block until released together - without
        // it the first thread would usually finish before the second began, and
        // the test would prove nothing.
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        tokenService.consume(raw, EmailTokenType.VERIFY_EMAIL);
                        successes.incrementAndGet();
                    } catch (Exception e) {
                        // Any failure counts: the losing thread may surface as a
                        // typed token exception or, if the two transactions
                        // collide at the database, a concurrency exception.
                        // Which one is not the point - "not a success" is.
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "threads did not finish in time");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, successes.get(),
                "exactly one consume must succeed; got " + successes.get());
        assertEquals(1, failures.get(),
                "exactly one consume must fail; got " + failures.get());

        // And the row must reflect a single consumption, not a double write.
        assertNotNull(emailTokenRepository.findByTokenHashWithUser(tokenHasher.hash(raw))
                .orElseThrow()
                .getUsedAt());
    }
}
