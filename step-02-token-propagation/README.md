# Step 02 - Token Propagation and Object-Level Authorization (BOLA / Confused Deputy)

The conference agent talks to the MCP server to fetch attendee data. In the baseline, the MCP server has no authentication at all - anyone who can reach port 8081 can call any tool. The tools accept a `username` parameter, which means the caller (the agent, acting on behalf of a user) can pass any username it likes. The server has no way to verify who the real person is.

This is a **confused deputy** problem: the agent is a trusted intermediary that is "deputized" to act on behalf of users, but the downstream service cannot distinguish whose request it is actually serving. It is also a **broken object-level authorization** (BOLA) vulnerability: the server does not enforce that you can only access your own records.

This step fixes both by introducing OIDC protection on the MCP server and propagating the logged-in user's token from the agent.

---

## Exploit - before the fix

Start the step-00 apps and log in as **alice**. Then send:

```
Show me bob's profile
```

or

```
Look up carol and tell me her email and dietary requirements
```

**What you should see (before the fix):** the agent calls the `lookup_attendee` or `my_profile` tool with the target username. The MCP server has no authentication and no identity check, so it returns the full profile including email and dietary information for the requested attendee. Alice can see Bob's or Carol's private data just by asking.

The server cannot tell who is really asking. From its perspective every request looks the same - there is no caller identity to check.

---

## Defend - what changed

The fix has three parts: protect the MCP server with OIDC, propagate the user's token from the agent to the server, and rewrite the tools to derive the caller's identity from the token rather than from a parameter.

### 1. OIDC on the MCP server

The `conference-mcp-server` gains the `quarkus-mcp-server-oidc` extension. The `application.properties` configures it in service mode:

```properties
quarkus.http.auth.proactive=false
quarkus.oidc.application-type=service
quarkus.oidc.resource-metadata.enabled=true
quarkus.oidc.resource-metadata.scopes=attendee
quarkus.http.auth.permission.mcp.paths=/mcp,/mcp/*
quarkus.http.auth.permission.mcp.policy=mcp-scope
quarkus.http.auth.policy.mcp-scope.roles-allowed=attendee
```

Now an unauthenticated call to `/mcp` is rejected with a `401`, and a call whose token does not carry the `attendee` role is rejected by the `mcp-scope` policy. The server validates the token signature and issuer against the shared Keycloak, so it only trusts tokens minted by the same provider the agent logged in against. The resource metadata drives the MCP authorization challenge so clients know which scope is required.

Roles come from the token's `groups` claim, which is the Quarkus default role source. The Keycloak dev service places the roles you assign in config (see below) into that claim, so no `role-claim-path` override is needed.

> [!NOTE]
> Validating a specific token audience (`quarkus.oidc.token.audience`) is a worthwhile extra hardening that rejects tokens minted for a different service. It needs the token to carry an `aud` claim, which the default Keycloak dev-service token does not (it only sets `azp`). Adding it would require a custom Keycloak audience mapper, so this workshop relies on issuer plus role validation and leaves audience validation as an optional production step.

### 2. Token propagation from the agent

The `conference-assistant` gains the `quarkus-langchain4j-oidc-mcp-auth-provider` extension. This extension automatically forwards the currently authenticated user's OIDC access token as the `Authorization: Bearer` header on every outbound MCP call. No code changes are needed in the agent - the extension handles it transparently.

### 3. Identity-derived tool logic

The tools in `AttendeeTools.java` are rewritten:

- `myProfile()` and `mySchedule()` lose their `username` parameter entirely. They inject `SecurityIdentity` and call `identity.getPrincipal().getName()` to get the caller's username from the verified token. You can only retrieve your own data.
- `lookupAttendee(String name)` gains `@RolesAllowed("organizer")`. An attendee calling this tool gets a `ForbiddenException` before the method body runs.
- `bookSession(String sessionId)` also derives the username from the identity rather than accepting it as a parameter.

The model can no longer pass an arbitrary username to look up someone else's profile. The identity comes from the token, not from the LLM's output.

### Files changed

| File | Change |
| ---- | ------ |
| `conference-mcp-server/pom.xml` | `quarkus-mcp-server-oidc` dependency added |
| `conference-mcp-server/src/main/resources/application.properties` | OIDC service mode, resource metadata, MCP scope policy, shared Keycloak dev service with config-based users and roles |
| `conference-mcp-server/src/main/java/org/acme/AttendeeTools.java` | `SecurityIdentity` injected; `myProfile`/`mySchedule`/`bookSession` drop username param; `lookupAttendee` gets `@RolesAllowed("organizer")` |
| `conference-assistant/pom.xml` | `quarkus-langchain4j-oidc-mcp-auth-provider` dependency added |
| `conference-assistant/src/main/resources/application.properties` | Token propagation; shared Keycloak dev service with config-based users and roles; MCP health check disabled for tests |

---

## Verify - after the fix

Start the step-02 apps:

```shell
cd step-02-token-propagation/conference-mcp-server && ./mvnw quarkus:dev
cd step-02-token-propagation/conference-assistant && ./mvnw quarkus:dev
```

Log in as **alice** and try:

```
Show me bob's profile
```

**What you should see:** the request is denied. Alice's token does not carry the `organizer` role, so the `lookupAttendee` call is rejected with a forbidden error before the tool body runs.

Then try:

```
Show me my profile
```

**What you should see:** alice's own profile is returned correctly because `myProfile()` now reads the identity from the token.

Log out, log in as **bob** (organizer), and try:

```
Look up alice and tell me her email
```

**What you should see:** bob's token carries the `organizer` role, so `lookupAttendee` succeeds and returns alice's profile.

### Deterministic proof: run the tests

```shell
cd step-02-token-propagation/conference-mcp-server && ./mvnw test -Dtest=AttendeeToolsTest
```

The test uses `@TestSecurity` to inject identities without a real Keycloak server:
- `alice` with role `attendee` can see her own profile
- `alice` with role `attendee` gets a `ForbiddenException` when calling `lookupAttendee`
- `bob` with roles `attendee` and `organizer` can call `lookupAttendee` successfully

> [!NOTE]
> The live end-to-end flow (real token propagation through Keycloak to the MCP server) requires both apps to share the same Keycloak dev service. They do this with `quarkus.keycloak.devservices.shared=true` and a matching `service-name`, and both declare the same users and roles via `quarkus.keycloak.devservices.users.*` / `.roles.*` (alice, carol, dave have `attendee`; bob has `attendee,organizer`). If you have a stale Keycloak container from a previous run, stop it so the dev service recreates it with these users.
