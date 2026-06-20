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
quarkus.oidc.application-type=service
quarkus.oidc.token.audience=conference-mcp
quarkus.oidc.roles.role-claim-path=realm_access/roles
quarkus.oidc.resource-metadata.enabled=true
quarkus.oidc.resource-metadata.scopes=attendee
quarkus.http.auth.permission.mcp.paths=/mcp,/mcp/*
quarkus.http.auth.permission.mcp.policy=mcp-scope
quarkus.http.auth.policy.mcp-scope.roles-allowed=attendee
```

The audience check (`conference-mcp`) rejects tokens that were not explicitly minted for this server. The `realm_access/roles` path tells Quarkus where to find role claims in the Keycloak JWT. The resource metadata is used by the MCP discovery so clients know what scopes are required.

The Keycloak realm JSON includes an audience mapper that adds `conference-mcp` to the audience of tokens scoped to this server.

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
| `conference-mcp-server/src/main/resources/application.properties` | OIDC service mode, audience, roles claim path, resource metadata, MCP scope policy |
| `conference-mcp-server/src/main/java/org/acme/AttendeeTools.java` | `SecurityIdentity` injected; `myProfile`/`mySchedule`/`bookSession` drop username param; `lookupAttendee` gets `@RolesAllowed("organizer")` |
| `conference-assistant/pom.xml` | `quarkus-langchain4j-oidc-mcp-auth-provider` dependency added |
| `conference-assistant/src/main/resources/application.properties` | Shared Keycloak dev service config; MCP health check disabled for tests |
| `conference-assistant/src/main/resources/javazone-realm.json` | Audience mapper for `conference-mcp` added to Keycloak realm |

---

## Verify - after the fix

Start the step-02 apps:

```shell
cd step-02-token-propagation/conference-mcp-server && ../../mvnw quarkus:dev
cd step-02-token-propagation/conference-assistant && ../../mvnw quarkus:dev
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
cd step-02-token-propagation/conference-mcp-server && ../../mvnw test -Dtest=AttendeeToolsTest
```

The test uses `@TestSecurity` to inject identities without a real Keycloak server:
- `alice` with role `attendee` can see her own profile
- `alice` with role `attendee` gets a `ForbiddenException` when calling `lookupAttendee`
- `bob` with roles `attendee` and `organizer` can call `lookupAttendee` successfully

> [!NOTE]
> The live end-to-end flow (real token propagation through Keycloak to the MCP server) requires both dev service containers to start cleanly and share the same Keycloak realm. If you have a stale Keycloak container from a previous run, stop it and let the dev service recreate it so that the realm with the audience mapper is applied.
