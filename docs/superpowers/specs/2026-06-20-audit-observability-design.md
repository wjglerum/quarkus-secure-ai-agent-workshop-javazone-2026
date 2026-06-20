# Audit Logging and Observability - Design

**Date:** 2026-06-20
**Status:** Approved (design), implementing
**Builds on:** [2026-06-19-javazone-secure-agent-workshop-design.md](2026-06-19-javazone-secure-agent-workshop-design.md)

## Summary

Two additions to the JavaZone secure agent workshop, both tied to the OWASP Top
10 for LLM Applications:

1. **Audit logging** folded into the existing `step-02-token-propagation` step.
   Once the caller's personal access token is propagated to the MCP server, the
   server finally knows *who* is asking, so it can record an auditable line for
   every tool call. Before propagation the server only saw a service identity, so
   audit logging would have been meaningless. This is the pedagogical hook.
2. A new **`step-05-observability`** step that stands up the Grafana LGTM stack
   via Quarkus dev services plus OpenTelemetry, then runs an OWASP **LLM10
   Unbounded Consumption** (denial of wallet) exploit and defends it with a
   consumption rate limit.

## OWASP mapping

Audit logging and observability are not a single OWASP item. They are the
detective and governance controls that cut across the list. The honest framing:

- **Audit logging** is a detective control supporting **LLM01** (prompt
  injection attempts become visible), **LLM06** (excessive agency: rejected
  privileged calls are logged), and **LLM02** (object-level denials are logged).
  OWASP lists logging and monitoring as a recommended mitigation under each of
  those entries.
- **Observability + the consumption cap** maps directly to **LLM10 Unbounded
  Consumption**: OWASP calls for monitoring token usage, latency, and resource
  consumption to catch denial of wallet and runaway loops, and for enforcing
  limits.

## Part A - Audit logging in step-02 (carried into 03, 04, 05)

The steps are cumulative, so the audit logging introduced in step-02 is
replicated into the step-03, step-04, and step-05 reference solutions.

### Mechanism

A CDI `@AroundInvoke` interceptor on the conference MCP server, applied to the
tool beans. No new dependency: it uses the JBoss logging and CDI already on the
classpath.

- `audit/Audited.java` - an interceptor binding annotation.
- `audit/AuditInterceptor.java` - the interceptor. Priority is set below the
  Quarkus security interceptor so it runs as the outermost wrapper. That lets it
  observe a `ForbiddenException` thrown by `@RolesAllowed` and record it as a
  `DENY`, then rethrow.
- `audit/AuditLog.java` - an `@ApplicationScoped` sink. It writes one structured
  single-line record per call to a dedicated `audit` logger category and keeps a
  bounded in-memory list of recent entries for tests and the Dev UI.
- `audit/AuditEntry.java` - an immutable record: timestamp, subject, tool,
  decision (`ALLOW` / `DENY`), outcome (`ok` / `error` / `forbidden`), and the
  redacted arguments.

`AttendeeTools` and `OrganizerTools` are annotated `@Audited` at class level.

### What is recorded, and the PII trap

The record carries the caller subject (from the propagated token), the tool
name, the decision, the outcome, and the arguments **with PII redacted**. The
`-parameters` compiler flag is already enabled, so the interceptor reads real
parameter names and masks any argument named `email` or `name` (for example
`carol` becomes `c***`, an email becomes `c***@***`).

The teaching point: naive audit logging that dumps full arguments re-leaks the
very PII that step-04 protects. Redaction is the lesson, and it links audit
logging back to LLM02.

### Cumulative payoff

Because step-03 gates organizer tools and step-04 keeps the BOLA fix, the audit
log in those steps shows real `DENY` lines when an attendee attempts a
privileged or cross-user call. The step-02 README gains a "now read the audit
log" beat after the exploit and after the fix.

### Test

`audit/AuditLogTest.java` (`@QuarkusTest` with `@TestSecurity`) asserts:

- alice calling `my_profile` produces an `ALLOW` entry for subject `alice`.
- alice calling `lookup_attendee` (organizer only) throws `ForbiddenException`
  and produces a `DENY` entry.
- the recorded arguments for the lookup are redacted (the raw name does not
  appear in the entry).

## Part B - step-05-observability (exploit then defend, LLM10)

`step-05-observability` is a copy of step-04 (which already carries the audit
logging) plus the observability stack and the consumption defense.

### Stack

Both apps gain:

- `io.quarkus:quarkus-micrometer-opentelemetry` - Micrometer plus OpenTelemetry
  metrics, traces, and logs, all signals on by default.
- `io.quarkus:quarkus-observability-devservices-lgtm` - starts the Grafana OTel
  LGTM container in dev mode and auto-wires `quarkus.otel.exporter.otlp.endpoint`.

In dev mode a chat turn becomes a distributed trace spanning agent to LLM to MCP
tool calls, with token-usage and latency metrics and the step-02 audit logs all
visible in Grafana. Observability is disabled under the `test` profile so the
test suite does not start the container.

### Exploit (LLM10 Unbounded Consumption / denial of wallet)

A crafted prompt drives runaway work, for example asking the agent to look up
every attendee one by one in a loop, or to produce a very large repeated output.
Grafana shows the token, latency, and call-count spike. This is the visible
payoff of having the stack.

### Defend

A consumption cap. The primary, deterministically testable fix is a per-method
`@RateLimit` (SmallRye Fault Tolerance) on a small `ConsumptionGuard` gate that
the WebSocket endpoint calls before invoking the model. When the limit is
exceeded the guard throws `RateLimitException`, which the endpoint catches and
turns into a friendly "slow down" message instead of unbounded model calls. A
second layer caps model output via configuration (max output tokens).

The rate limit is global per method for workshop simplicity; per-user keying is
noted as a production enhancement.

### Test

`ratelimit/ConsumptionGuardTest.java` (`@QuarkusTest`) calls the gate up to the
limit successfully and asserts the next call throws `RateLimitException`. No
model and no running server logic is exercised, consistent with the workshop's
deterministic-proof philosophy.

## Repository and docs changes

- `step-02-token-propagation` ... `step-04-sensitive-disclosure`
  `conference-mcp-server`: add the four `audit/` classes, annotate the tool
  beans, add `AuditLogTest`.
- `step-02` README: add the audit-logging exploit and fix beats and a files
  table row.
- New `step-05-observability/` (agent plus MCP server pair, cumulative on
  step-04) with its own README in the exploit-then-fix format.
- Root `pom.xml`: add the `step-05-observability` module.
- Root `README.md`: add the step-05 row (LLM10) and an audit-logging mention on
  the step-02 row.
- `docs/slides/presenter-deck.html`: add an audit-logging beat to the token
  propagation slide and a new LLM10 observability slide; mark LLM10 as covered
  on the OWASP overview slide.
- This supersedes the "Observability / auditing module" out-of-scope note in the
  prior design doc, which is updated to point here.

## Decisions

- Audit logging emits a clean single-line structured record under a dedicated
  `audit` logger category rather than reformatting all dev output as JSON, which
  keeps the dev console readable during the workshop.
- The consumption rate limit is global per method, not per user. Per-user keying
  is left as a production note.
- Both apps run the observability stack so the trace spans the full agent to MCP
  path. The LGTM dev service is shared and reused across the two apps, the same
  way the Keycloak dev service already is.

## Out of scope

- Per-user rate-limit keying.
- Custom Grafana dashboards beyond the bundled Quarkus ones.
- Tamper-evident or persisted audit storage (the log line plus bounded in-memory
  buffer is enough for the workshop).
