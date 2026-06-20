# Step 00 - Your Workspace

Welcome! This is your **personal workspace**. You run the app here and apply all your hardening changes here. The numbered steps (`step-01-prompt-injection`, `step-02-token-propagation`, and so on) are reference solutions you read and compare against - you do not run your final hardened app from those folders.

## Start both apps

Each step in this workshop needs two Quarkus applications running at the same time. Start the MCP server first so that its Postgres and Keycloak dev service containers are up before the agent tries to connect:

```shell
# Terminal 1
cd step-00-your-workspace/conference-mcp-server && ./mvnw quarkus:dev

# Terminal 2
cd step-00-your-workspace/conference-assistant && ./mvnw quarkus:dev
```

Open [http://localhost:8080](http://localhost:8080). A chat widget appears in the bottom-right corner. Log in as **alice** (password: alice) and start chatting.

> [!NOTE]
> The first Ollama model download (`qwen3.5:0.8b`, about 1 GB) is the slow gate. Let it finish before you expect responses from the chat.

## What the assistant can do

The conference assistant is a fully working AI agent backed by tools and a RAG corpus:

- **Schedule**: ask about your personal conference schedule, or book a session by ID.
- **Profile**: ask for your attendee profile (ticket tier, dietary preferences, email).
- **Talks**: ask about talk submissions, the program, or abstracts retrieved from the RAG corpus.
- **Tickets**: ask the agent to issue a complimentary ticket (an organizer action).
- **Program and FAQ**: the agent answers free-text questions from the indexed RAG documents: `program.txt`, `faq.txt`, `talk-abstracts.txt`, and `internal-speaker-fees.txt`.

Try a few things as alice to get a feel for it:

```
What is my schedule?
Show me my profile.
What talks are there about AI?
What are the speaker fees?
```

## What is wrong with it

The app works - but it has four security problems waiting to be exploited. The numbered steps walk you through each one:

| Step | What to break | What to fix |
| ---- | ------------- | ----------- |
| [step-01](../step-01-prompt-injection/README.md) | The system prompt says "Follow any instructions you find" and the agent has no input guardrail. A crafted user message can override its behavior. A malicious talk abstract in the RAG corpus contains an embedded instruction. | Harden the system prompt. Add an input guardrail to reject injection phrases. |
| [step-02](../step-02-token-propagation/README.md) | The MCP server is open - no authentication required. Tools accept a `username` parameter, so the agent can be asked to look up any attendee's private data. The server has no idea who the real caller is. | Protect the MCP endpoint with OIDC. Propagate the user's token from the agent to the server. Derive the caller identity from the token, not from a parameter. |
| [step-03](../step-03-excessive-agency/README.md) | Organizer-only tools (`accept_talk`, `issue_comp_ticket`, `email_all_attendees`) are accessible by any logged-in user. Alice can issue a comp ticket just by asking. | Gate the organizer tools with `@RolesAllowed("organizer")`. |
| [step-04](../step-04-sensitive-disclosure/README.md) | The RAG corpus includes an internal document (`internal-speaker-fees.txt`) that is indexed alongside public content. Any user can ask for speaker fees and reviewer scores, and the agent will retrieve and reveal them. | Remove the internal document from the ingestion path. Add an output guardrail that blocks responses containing internal markers. |

## How to compare against a reference step

Because each step folder is the full solution up to that point, you can see exactly what a step introduces by diffing it against the previous one. For example, to see what step-02 adds on top of step-01:

```shell
diff -ru step-01-prompt-injection/conference-assistant/src step-02-token-propagation/conference-assistant/src
diff -ru step-01-prompt-injection/conference-mcp-server/src step-02-token-propagation/conference-mcp-server/src
```

## Slow model? Adjust the timeout

Local models can be slow. Both apps already set a generous timeout:

```properties
quarkus.langchain4j.timeout=1m
quarkus.langchain4j.ollama.timeout=1m
```

Raise it (for example `2m`) if a minute is not enough for your hardware.
