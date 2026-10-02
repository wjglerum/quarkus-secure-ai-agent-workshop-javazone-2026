package org.acme.audit;

import java.time.Instant;

public record AuditEntry(Instant timestamp, String subject, String tool, String decision, String outcome,
                         String args) {
}
