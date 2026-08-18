package com.arshraj.vakilconnect.email;

import com.arshraj.vakilconnect.email.event.SendEmailRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract and redaction for the transport value objects. Pure unit test.
 */
@DisplayName("EmailMessage / SendEmailRequestedEvent")
class EmailMessageTest {

    private static final String LINK =
            "https://vakil-connect-sage.vercel.app/verify-email?token=SUPERSECRETTOKENVALUE";

    private static EmailMessage valid() {
        return new EmailMessage("user@example.com", "Verify your email",
                "<a href=\"" + LINK + "\">Verify</a>", LINK, "verification");
    }

    // ------------------------------------------------------------ validation

    @Test
    @DisplayName("accepts a well-formed message")
    void acceptsValid() {
        assertDoesNotThrow(EmailMessageTest::valid);
    }

    @Test
    @DisplayName("rejects a blank recipient")
    void rejectsBlankRecipient() {
        assertThrows(IllegalArgumentException.class,
                () -> new EmailMessage("  ", "s", "h", "t", "tag"));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailMessage(null, "s", "h", "t", "tag"));
    }

    @Test
    @DisplayName("rejects a blank subject")
    void rejectsBlankSubject() {
        assertThrows(IllegalArgumentException.class,
                () -> new EmailMessage("a@b.com", " ", "h", "t", "tag"));
    }

    @Test
    @DisplayName("rejects a message with neither html nor text")
    void rejectsEmptyBody() {
        // An email with no body is always a bug, and the provider would reject
        // it anyway - better to fail at construction than after a network call.
        assertThrows(IllegalArgumentException.class,
                () -> new EmailMessage("a@b.com", "s", null, null, "tag"));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailMessage("a@b.com", "s", "  ", "  ", "tag"));
    }

    @Test
    @DisplayName("accepts html-only and text-only")
    void acceptsEitherBody() {
        assertDoesNotThrow(() -> new EmailMessage("a@b.com", "s", "<p>x</p>", null, "tag"));
        assertDoesNotThrow(() -> new EmailMessage("a@b.com", "s", null, "x", "tag"));
    }

    @Test
    @DisplayName("rejects a blank tag")
    void rejectsBlankTag() {
        // The tag becomes a metric label; a blank one produces an unlabelled
        // time series that cannot be attributed to anything.
        assertThrows(IllegalArgumentException.class,
                () -> new EmailMessage("a@b.com", "s", "h", "t", ""));
    }

    @Test
    @DisplayName("SendEmailRequestedEvent rejects a null message")
    void eventRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new SendEmailRequestedEvent(null));
    }

    // ------------------------------------------------------------- redaction

    @Test
    @DisplayName("EmailMessage.toString() leaks neither the body nor the recipient")
    void messageToStringRedacts() {
        String s = valid().toString();

        // The single most important assertion in this class: a record's DEFAULT
        // toString() would print every component, putting a live single-use
        // link into any log line that touched the object.
        assertFalse(s.contains("SUPERSECRETTOKENVALUE"), "token leaked: " + s);
        assertFalse(s.contains(LINK), "link leaked: " + s);
        assertFalse(s.contains("user@example.com"), "recipient leaked: " + s);

        // Still useful for debugging.
        assertTrue(s.contains("verification"));
        assertTrue(s.contains("Verify your email"));
    }

    @Test
    @DisplayName("SendEmailRequestedEvent.toString() redacts independently")
    void eventToStringRedacts() {
        String s = new SendEmailRequestedEvent(valid()).toString();

        // Redacted at BOTH levels deliberately, so neither one alone is
        // load-bearing: a future change to EmailMessage cannot silently start
        // leaking through the event.
        assertFalse(s.contains("SUPERSECRETTOKENVALUE"), "token leaked: " + s);
        assertFalse(s.contains("user@example.com"), "recipient leaked: " + s);
        assertTrue(s.contains("verification"));
    }

    @Test
    @DisplayName("EmailProperties.toString() never prints the API key")
    void propertiesToStringRedactsApiKey() {
        EmailProperties props = new EmailProperties(
                "resend", "re_a_fake_key_value", "noreply@example.com", "VakilConnect");

        String s = props.toString();

        assertFalse(s.contains("re_a_fake_key_value"), "API KEY LEAKED: " + s);
        assertTrue(s.contains("<redacted>"));
        assertEquals(true, s.contains("noreply@example.com"),
                "the from address is not a secret and stays visible for debugging");
    }
}
