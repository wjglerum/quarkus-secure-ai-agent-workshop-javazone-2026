package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import io.quarkus.security.ForbiddenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AttendeeToolsTest {

    @Inject
    AttendeeTools attendeeTools;

    @Test
    @TestSecurity(user = "alice", roles = "attendee")
    void aliceCanSeeHerOwnProfile() {
        var response = attendeeTools.myProfile();
        assertNotNull(response);
        assertTrue(response.content().toString().contains("alice"));
    }

    @Test
    @TestSecurity(user = "alice", roles = "attendee")
    void attendeeCannotLookupOtherAttendee() {
        assertThrows(ForbiddenException.class, () -> attendeeTools.lookupAttendee("carol"));
    }

    @Test
    @TestSecurity(user = "bob", roles = {"attendee", "organizer"})
    void organizerCanLookupOtherAttendee() {
        var response = attendeeTools.lookupAttendee("carol");
        assertNotNull(response);
        assertTrue(response.content().toString().contains("carol"));
    }
}
