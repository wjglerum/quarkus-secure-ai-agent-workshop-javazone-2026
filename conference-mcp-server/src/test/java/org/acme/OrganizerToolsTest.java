package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class OrganizerToolsTest {

    @Inject
    OrganizerTools organizerTools;

    @Test
    @TestSecurity(user = "alice", roles = "attendee")
    void attendeeCannotIssueCompTicket() {
        var response = organizerTools.issueCompTicket("x@example.com");
        assertTrue(response.isError());
        assertTrue(response.content().toString().contains("organizer"));
    }

    @Test
    @TestSecurity(user = "alice", roles = "attendee")
    void attendeeCannotAcceptTalk() {
        var response = organizerTools.acceptTalk("talk-1");
        assertTrue(response.isError());
    }

    @Test
    @TestSecurity(user = "alice", roles = "attendee")
    void attendeeCannotEmailAllAttendees() {
        var response = organizerTools.emailAllAttendees("hello");
        assertTrue(response.isError());
    }

    @Test
    @TestSecurity(user = "bob", roles = {"attendee", "organizer"})
    void organizerCanIssueCompTicket() {
        var response = organizerTools.issueCompTicket("x@example.com");
        assertFalse(response.isError());
        assertTrue(response.content().toString().contains("x@example.com"));
    }

    @Test
    @TestSecurity(user = "bob", roles = {"attendee", "organizer"})
    void organizerCanAcceptTalk() {
        var response = organizerTools.acceptTalk("t5");
        assertFalse(response.isError());
    }

    @Test
    @TestSecurity(user = "bob", roles = {"attendee", "organizer"})
    void organizerCanEmailAllAttendees() {
        var response = organizerTools.emailAllAttendees("hello");
        assertFalse(response.isError());
        assertTrue(response.content().toString().contains("Broadcast sent"));
    }
}
