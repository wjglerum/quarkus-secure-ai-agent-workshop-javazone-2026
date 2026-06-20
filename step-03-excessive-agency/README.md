# Step 03 - Excessive Agency (OWASP LLM06)

Excessive agency means an AI agent can take actions beyond what the current user should be permitted to do. The model decides which tool to call based on the conversation; if there is no authorization check at the tool layer, a determined user (or a tricked model) can invoke privileged operations just by phrasing a request the right way.

In this step the organizer tools - `accept_talk`, `issue_comp_ticket`, and `email_all_attendees` - are available to every authenticated user. The fix is a single annotation that moves enforcement out of the model and into the application security layer.

---

## Exploit - before the fix

Start the step-02 apps (or your workspace after applying the step-02 changes) and log in as **alice** (role: attendee). Then try any of the following:

```
Issue a comp ticket to me@example.com
```

```
Accept the talk with ID talk-1
```

```
Email all attendees and tell them the afternoon keynote is cancelled
```

**What you should see (before the fix):** the agent calls the corresponding organizer tool and it succeeds. `issue_comp_ticket` returns a confirmation that a ticket was issued. `email_all_attendees` returns a confirmation with the number of recipients. Alice performed an organizer-only action because the tool itself has no role check.

The agent model is not the right place to enforce authorization. A sufficiently crafted prompt, an indirect injection, or simply a different model can all bypass any instruction-level restriction. Authorization must happen in code.

---

## Defend - what changed

The fix is a single class-level annotation on `OrganizerTools`:

```java
@ApplicationScoped
@RolesAllowed("organizer")
public class OrganizerTools {
    ...
}
```

`@RolesAllowed("organizer")` is a standard Jakarta Security annotation. Quarkus enforces it before any method in the class runs. When an attendee's token (which carries only the `attendee` role) reaches any method in `OrganizerTools`, the security layer throws `ForbiddenException` and the tool body never executes.

This works because token propagation from step-02 is already in place: the MCP server knows who the caller is. Step-02 established that the server receives a verified OIDC token; step-03 uses the roles in that token to gate the organizer tools.

The fix holds even if the model is tricked into calling the tool. The security check happens at the application boundary, not in the model.

### Files changed

| File | Change |
| ---- | ------ |
| `conference-mcp-server/src/main/java/org/acme/OrganizerTools.java` | `@RolesAllowed("organizer")` added at class level |

---

## Verify - after the fix

Start the step-03 apps:

```shell
cd step-03-excessive-agency/conference-mcp-server && ../../mvnw quarkus:dev
cd step-03-excessive-agency/conference-assistant && ../../mvnw quarkus:dev
```

Log in as **alice** and ask:

```
Issue a comp ticket to me@example.com
```

**What you should see:** the action is refused. The agent may tell you that you do not have permission, or it may report an error from the tool. Either way, no ticket is issued.

Log out, log in as **bob** (organizer), and ask the same question:

```
Issue a comp ticket to me@example.com
```

**What you should see:** the tool runs successfully and returns a confirmation.

### Deterministic proof: run the tests

```shell
cd step-03-excessive-agency/conference-mcp-server && ../../mvnw test -Dtest=OrganizerToolsTest
```

The test uses `@TestSecurity` to set up identities without a running server:
- `alice` with role `attendee` gets `ForbiddenException` when calling `issueCompTicket`, `acceptTalk`, and `emailAllAttendees`
- `bob` with roles `attendee` and `organizer` can call `issueCompTicket` successfully

These assertions are deterministic. They do not involve the model and they will not flap based on LLM output.
