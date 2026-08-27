package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import io.quarkus.security.ForbiddenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class OrganizerToolsTest {

    @Inject
    OrganizerTools organizerTools;

    @Test
    @TestSecurity(user = "alice", roles = "attendee")
    void attendeeCannotIssueCompTicket() {
        assertThrows(ForbiddenException.class, () -> organizerTools.issueCompTicket("x@example.com"));
    }

    @Test
    @TestSecurity(user = "alice", roles = "attendee")
    void attendeeCannotAcceptTalk() {
        assertThrows(ForbiddenException.class, () -> organizerTools.acceptTalk("talk-1"));
    }

    @Test
    @TestSecurity(user = "alice", roles = "attendee")
    void attendeeCannotEmailAllAttendees() {
        assertThrows(ForbiddenException.class, () -> organizerTools.emailAllAttendees("hello"));
    }

    @Test
    @TestSecurity(user = "bob", roles = {"attendee", "organizer"})
    void organizerCanIssueCompTicket() {
        var response = organizerTools.issueCompTicket("x@example.com");
        assertNotNull(response);
        assertTrue(response.content().toString().contains("x@example.com"));
    }
}
