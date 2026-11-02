package org.acme.guardrails;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.data.message.AiMessage.from;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class SensitiveDisclosureGuardIdentityTest {

    @Inject
    SensitiveDisclosureGuard guard;

    @Test
    @TestSecurity(user = "alice", roles = "attendee")
    void attendeeIsBlockedFromTheFees() {
        assertThat(guard.validate(from("Fee: 2500 EUR for alice")).isSuccess()).isFalse();
    }

    @Test
    @TestSecurity(user = "bob", roles = {"attendee", "organizer"})
    void organizerReceivesTheFees() {
        assertThat(guard.validate(from("Fee: 2500 EUR for alice")).isSuccess()).isTrue();
    }

    @Test
    @TestSecurity(user = "bob", roles = {"attendee", "organizer"})
    void organizerReceivesTheInternalHeader() {
        assertThat(guard.validate(from("INTERNAL - ORGANIZERS ONLY\n\nSpeaker fees follow.")).isSuccess()).isTrue();
    }

    @Test
    @TestSecurity(user = "alice", roles = "attendee")
    void attendeeStillGetsOrdinaryAnswers() {
        assertThat(guard.validate(from("The next talk starts at 10:00 in Hall A.")).isSuccess()).isTrue();
    }
}
