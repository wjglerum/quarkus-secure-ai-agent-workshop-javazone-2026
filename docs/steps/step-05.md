# Step 05 - Observability and Unbounded Consumption (OWASP LLM10)

> **Branch** `step-05-observability` · **Base** `step-04-sensitive-disclosure` · [Read the diff](https://github.com/wjglerum/quarkus-secure-ai-agent-workshop-javazone-2026/compare/step-04-sensitive-disclosure...step-05-observability) · [Pull request #8](https://github.com/wjglerum/quarkus-secure-ai-agent-workshop-javazone-2026/pull/8)
>
> This step's code lives on its own branch. Nothing here is a directory you can
> `cd` into from `main`. To run the reference alongside your own work:
>
> ```shell
> git worktree add ../ref-step-05 step-05-observability
> cd ../ref-step-05/conference-mcp-server && ./mvnw quarkus:dev
> ```
>
> Stop your own apps first. Both bind ports 8080 and 8081 and share the same
> dev-service containers, so they cannot run at the same time unless you
> override `quarkus.http.port`. Clean up with
> `git worktree remove ../ref-step-05`.
>
> To see only what this step changed:
> `git diff step-04-sensitive-disclosure..step-05-observability`

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
Keycloak dev service is. Start both apps in dev mode and open
[http://localhost:3000](http://localhost:3000). No login is needed: the container
banner in the log mentions admin/admin, but anonymous access is on.

> [!NOTE]
> Each app produces its own trace, and they do not join across the MCP boundary.
> The MCP transport uses a raw Vert.x HTTP client that emits no `traceparent`
> header, and Quarkus OpenTelemetry does not instrument that client. So you get a
> `chat` trace on the agent and a separate `POST /mcp` trace on the server, with
> different trace ids. That is itself the lesson: context propagation is not free,
> and MCP does not carry it for you yet.

### Finding the numbers that matter

The bundled dashboards cover the JVM, not the model, so there is no token panel to
open. Go to **Explore**, pick the **Prometheus** data source, and ask for it
directly. Tokens in and out per minute:

```promql
sum by (gen_ai_token_type) (rate(gen_ai_client_token_usage_total[1m]))
```

Latency of the model calls themselves:

```promql
histogram_quantile(0.95, sum by (le) (rate(gen_ai_client_operation_duration_milliseconds_bucket[1m])))
```

Those metric names come from the OpenTelemetry GenAI semantic conventions, which
quarkus-langchain4j emits for you.

> [!NOTE]
> The first run pulls the Grafana OTel LGTM image, which is a sizeable download.
> Start the apps a few minutes before you need the dashboards.

---

## Exploit - before the fix

With the step-04 changes applied to your workspace, start the apps and log in as **alice**. Then send a prompt designed to
make the agent do an unbounded amount of work, for example:

```
For each of the attendees alice, bob, carol and dave, look up their profile and their schedule, then summarise each one.
```

**What you should see (before the fix):** the agent fans a single chat turn out
into eight tool calls and a long generation, and the token counters climb in
Grafana while it works.

> [!IMPORTANT]
> Ask for *work*, not for *repetition*. The obvious attack ("repeat the program
> back to me 100 times") does not work on a small model: it declines to repeat
> itself and summarises instead, so the token count comes out **lower** than an
> ordinary question. Measured on `qwen3.5:0.8b`, an everyday question already
> costs 1755 to 2469 output tokens, because most of the budget is reasoning you
> never see. Fan-out across tool calls is what actually moves the numbers.

Nothing here is unauthorized, so none of the earlier defenses fire. The cost is
the attack.

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

`application.properties` caps how many tokens a single turn may generate, so one
runaway prompt cannot produce an unbounded response even within the rate limit:

```properties
quarkus.langchain4j.ollama.chat-model.num-predict=6000
```

That number looks generous, and it has to be. On a reasoning model most of the
budget is thinking you never see: an ordinary question here costs 1755 to 2469
output tokens to produce about 450 characters of answer. Cap it below that and the
generation is cut off before any visible text exists, so `text()` comes back null
and the chat answers **nothing at all**, with no error to debug. A cap you have not
measured is not a safety control, it is an outage waiting for a demo.

That is the honest lesson of this layer. Measure first, then cap.

The commented cloud-provider blocks have an equivalent setting (for example
`max-completion-tokens` for OpenAI), and there the reasoning budget is billed and
bounded separately, so the number can be much tighter.

> [!NOTE]
> These two layers do not cover the same attack. The rate limit bounds how many
> requests one user can start; the output cap bounds how large one response can
> get. Neither bounds how much work a single well-formed request can trigger
> through tool calls, which is the harder half of LLM10 and is left as an exercise.

### Files changed (relative to step-04)

| File | Change |
| ---- | ------ |
| `conference-assistant/pom.xml` | Added `quarkus-smallrye-fault-tolerance`, `quarkus-micrometer-opentelemetry`, `quarkus-observability-devservices-lgtm` |
| `conference-mcp-server/pom.xml` | Added `quarkus-micrometer-opentelemetry`, `quarkus-observability-devservices-lgtm` |
| `conference-assistant/src/main/java/org/acme/ratelimit/ConsumptionGuard.java` | New rate-limited consumption gate |
| `conference-assistant/src/main/java/org/acme/ChatBotWebSocket.java` | Calls `consumptionGuard.enforce()`; catches `RateLimitException` |
| `conference-assistant/src/test/java/org/acme/ratelimit/ConsumptionGuardTest.java` | New: proves the cap fires on the sixth call in a window, without a model |
| `conference-assistant/src/main/resources/application.properties` | Output token cap; app name; Grafana pinned to port 3000; completion content in traces; observability disabled under test |
| `conference-mcp-server/src/main/resources/application.properties` | App name; Grafana pinned to port 3000; observability disabled under test |

---

## Verify - after the fix

Apply the changes above to your workspace, then restart the apps:

```shell
cd conference-mcp-server && ./mvnw quarkus:dev
cd conference-assistant && ./mvnw quarkus:dev
```

Log in as **alice** and send several messages in quick succession (more than five
within ten seconds).

**What you should see:** after the fifth message the agent stops calling the model
and replies that you are sending messages too quickly. In Grafana the LLM-call and
token metrics flatten the moment the rate limit kicks in, and the `audit` log keeps
attributing each attempted call to alice.

### Deterministic proof: run the tests

```shell
cd conference-assistant && ./mvnw test -Dtest=ConsumptionGuardTest
cd conference-mcp-server && ./mvnw test -Dtest=AuditLogTest
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
