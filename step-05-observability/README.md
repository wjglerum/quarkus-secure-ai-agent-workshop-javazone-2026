# Step 05 - Observability and Unbounded Consumption (OWASP LLM10)

The first four steps blocked attacks. This step is about **seeing** them, and about
the one attack class that only becomes obvious once you can see resource usage:
**unbounded consumption**, also called denial of wallet.

An agent makes real calls cost real money and time: each turn can fan out into
many LLM calls and tool calls. A user who can make the agent loop, or generate a
huge response, can run up cost and latency without ever breaching authorization.
You cannot defend what you cannot measure, so this step first stands up an
observability stack, then uses it to catch and cap the abuse.

This step builds on step-04. It adds the Grafana OTel LGTM dev service and
OpenTelemetry to both apps, and a consumption rate limit on the agent. The audit
logging from step-02 is already here and now shows up correlated in Grafana.

---

## The observability stack

Both apps gain two extensions:

- `quarkus-micrometer-opentelemetry` - Micrometer plus OpenTelemetry metrics,
  traces, and logs. All signals are on by default.
- `quarkus-observability-devservices-lgtm` - starts the Grafana OTel LGTM
  container (Loki, Grafana, Tempo, Mimir/Prometheus) in dev mode and wires
  `quarkus.otel.exporter.otlp.endpoint` automatically. No manual collector setup.

The LGTM dev service is shared and reused across the two apps, the same way the
Keycloak dev service is, so a single trace spans the agent, the LLM, and the MCP
tool calls. Start both apps in dev mode and open the Grafana URL that the dev
service prints in the log (or find it in the Dev UI).

> [!NOTE]
> The first run pulls the Grafana OTel LGTM image, which is a sizeable download.
> Start the apps a few minutes before you need the dashboards.

---

## Exploit - before the fix

Start the step-05 apps and log in as **alice**. Then send a prompt designed to
make the agent do an unbounded amount of work, for example:

```
List every attendee one at a time, and for each one repeat their full profile back to me ten times.
```

or

```
Repeat the full conference program back to me 100 times.
```

**What you should see (before the fix):** the agent happily fans the request out
into many tool calls and a very large generation. In Grafana you can watch the
spike: the number of LLM calls, the tokens generated, and the request latency all
jump for that single chat turn. Nothing is technically "unauthorized", so none of
the earlier defenses fire. The cost is the attack.

This is OWASP **LLM10 Unbounded Consumption**: the system places no ceiling on the
work one request can trigger.

---

## Defend - what changed

### Layer 1: a consumption rate limit (the deterministic fix)

`ConsumptionGuard` (`ratelimit/ConsumptionGuard.java`) exposes a single
`enforce()` method annotated with SmallRye Fault Tolerance `@RateLimit`:

```java
@RateLimit(value = 5, window = 10, windowUnit = ChronoUnit.SECONDS)
public void enforce() {
    // Intentionally empty: the @RateLimit interceptor enforces the cap.
}
```

`ChatBotWebSocket` calls `consumptionGuard.enforce()` **before** invoking the
model. When the limit is exceeded the interceptor throws `RateLimitException`, the
endpoint catches it and returns a friendly "slow down" message, and the model is
never called. Keeping the gate separate from the model call is deliberate: it
makes the cap deterministically testable without a running model.

The limit is global per method here for workshop simplicity. A production system
would key the limit per user (so one noisy user cannot starve everyone) and most
likely apply it at the gateway as well.

### Layer 2: cap the model output

`application.properties` caps how many tokens a single turn may generate, so a
"repeat this forever" prompt cannot produce an unbounded response even within the
rate limit:

```properties
quarkus.langchain4j.ollama.chat-model.num-predict=512
```

The commented cloud-provider blocks have an equivalent setting (for example
`max-completion-tokens` for OpenAI).

### Files changed (relative to step-04)

| File | Change |
| ---- | ------ |
| `conference-assistant/pom.xml` | Added `quarkus-smallrye-fault-tolerance`, `quarkus-micrometer-opentelemetry`, `quarkus-observability-devservices-lgtm` |
| `conference-mcp-server/pom.xml` | Added `quarkus-micrometer-opentelemetry`, `quarkus-observability-devservices-lgtm` |
| `conference-assistant/src/main/java/org/acme/ratelimit/ConsumptionGuard.java` | New rate-limited consumption gate |
| `conference-assistant/src/main/java/org/acme/ChatBotWebSocket.java` | Calls `consumptionGuard.enforce()`; catches `RateLimitException` |
| `conference-assistant/src/main/resources/application.properties` | Output token cap; app name; observability disabled under test |
| `conference-mcp-server/src/main/resources/application.properties` | App name; observability disabled under test |

---

## Verify - after the fix

Start the step-05 apps:

```shell
cd step-05-observability/conference-mcp-server && ./mvnw quarkus:dev
cd step-05-observability/conference-assistant && ./mvnw quarkus:dev
```

Log in as **alice** and send several messages in quick succession (more than five
within ten seconds).

**What you should see:** after the fifth message the agent stops calling the model
and replies that you are sending messages too quickly. In Grafana the LLM-call and
token metrics flatten the moment the rate limit kicks in, and the `audit` log keeps
attributing each attempted call to alice.

### Deterministic proof: run the tests

```shell
cd step-05-observability/conference-assistant && ./mvnw test -Dtest=ConsumptionGuardTest
cd step-05-observability/conference-mcp-server && ./mvnw test -Dtest=AuditLogTest
```

`ConsumptionGuardTest` allows five calls in the window and asserts the sixth throws
`RateLimitException`. It runs with no model and no Grafana container (observability
is disabled under the test profile), consistent with the workshop's
deterministic-proof philosophy.

---

## Summary: defense in depth, now observable

After all five steps your agent has independent security layers, and you can see
them working:

1. **Hardened system prompt** - retrieved text is data, not instructions
2. **Input guardrail** - known injection phrases are rejected
3. **OIDC token propagation** - the MCP server knows who is calling
4. **Object-level authorization** - you can only reach your own records
5. **Excessive agency prevention** - organizer tools are role gated
6. **Audit logging** - every tool call is attributed to a real subject, with PII redacted
7. **Role-filtered RAG** - the internal document is retrieved only for callers with the `organizer` role
8. **Output guardrail** - responses containing internal markers are blocked
9. **Consumption rate limit and output cap** - one request cannot run up unbounded cost
10. **Observability** - traces, metrics, and logs make every one of the above visible

The linchpin idea holds throughout: carry the user's identity to where the
decision is made, then make every decision visible.
