package org.acme.audit;

import java.time.Instant;

/**
 * One audit record for a single MCP tool invocation.
 *
 * @param timestamp when the call happened
 * @param subject   the caller derived from the propagated token (or "anonymous")
 * @param tool      the tool method name
 * @param decision  "ALLOW" if the call ran, "DENY" if authorization rejected it
 * @param outcome   "ok", "error", or "forbidden"
 * @param args      the call arguments with PII redacted
 */
public record AuditEntry(Instant timestamp, String subject, String tool, String decision, String outcome,
                         String args) {
}
