package org.acme;

import io.quarkus.security.ForbiddenException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.acme.audit.AuditEntry;
import org.acme.audit.AuditLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuditLogTest {

    @Inject
    AttendeeTools attendeeTools;

    @Inject
    AuditLog auditLog;

    @Test
    @TestSecurity(user = "alice", roles = "attendee")
    void allowedCallIsAuditedWithSubject() {
        attendeeTools.myProfile();

        AuditEntry entry = lastEntryFor("myProfile");
        assertEquals("alice", entry.subject());
        assertEquals("ALLOW", entry.decision());
        assertEquals("ok", entry.outcome());
    }

    @Test
    @TestSecurity(user = "alice", roles = "attendee")
    void deniedCallIsAuditedAndArgumentsRedacted() {
        assertThrows(ForbiddenException.class, () -> attendeeTools.lookupAttendee("carol"));

        AuditEntry entry = lastEntryFor("lookupAttendee");
        assertEquals("alice", entry.subject());
        assertEquals("DENY", entry.decision());
        assertEquals("forbidden", entry.outcome());
        assertFalse(entry.args().contains("carol"), "name argument should be redacted");
        assertTrue(entry.args().contains("c***"), "redacted name should be present");
    }

    private AuditEntry lastEntryFor(String tool) {
        List<AuditEntry> entries = auditLog.entries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).tool().equals(tool)) {
                return entries.get(i);
            }
        }
        throw new AssertionError("no audit entry recorded for tool " + tool);
    }
}
