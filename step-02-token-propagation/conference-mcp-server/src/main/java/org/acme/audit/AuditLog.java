package org.acme.audit;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Sink for tool-call audit records. Each call is written as a single structured
 * line under the dedicated "audit" logger category and kept in a bounded
 * in-memory buffer for tests and the Dev UI.
 *
 * <p>Logging only becomes meaningful once the caller's token is propagated to the
 * MCP server (step-02): before that the server cannot tell who is asking.
 */
@ApplicationScoped
public class AuditLog {

    private static final Logger LOG = Logger.getLogger("audit");
    private static final int MAX_ENTRIES = 200;

    private final Deque<AuditEntry> entries = new ArrayDeque<>();

    public synchronized void record(String subject, String tool, String args, String decision, String outcome) {
        AuditEntry entry = new AuditEntry(Instant.now(), subject, tool, decision, outcome, args);
        entries.addLast(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
        LOG.infof("subject=%s tool=%s decision=%s outcome=%s args=%s", subject, tool, decision, outcome, args);
    }

    public synchronized List<AuditEntry> entries() {
        return new ArrayList<>(entries);
    }
}
