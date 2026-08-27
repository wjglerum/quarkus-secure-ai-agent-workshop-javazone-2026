# Securing AI Agents with Quarkus and LangChain4j

In this workshop you get a **complete conference assistant** that already works end to end. The catch: it is deliberately insecure. Your job is to attack it, understand why each vulnerability exists, and then apply the fixes. The pattern repeats once per step, one security topic at a time. The first four steps are the roughly two hour core; the fifth (observability) is an optional extension for a longer slot.

The topics follow the OWASP Top 10 for LLM Applications:

- **Prompt Injection** (LLM01) - an over-trusting system prompt and no input guardrail
- **Broken Object-Level Authorization** (BOLA) - an unauthenticated MCP server with a confused-deputy flaw; the fix also adds PII-safe audit logging once the caller's token is propagated
- **Excessive Agency** (LLM06) - privileged organizer tools accessible by any authenticated user
- **Sensitive Information Disclosure** (LLM02) - internal documents in the RAG corpus with no output guardrail
- **Unbounded Consumption** (LLM10) - no ceiling on the work one request can trigger, observed with a Grafana LGTM stack and capped with a rate limit

## How the repository is laid out

**`main` is the vulnerable app, and it is your workspace.** The two applications sit at the repository root. You clone, you run, you attack, and you apply every hardening change right here.

**Each step is a branch, and each branch is a pull request.** The five reference solutions live on branches chained one after another, so the pull request for a step contains *only* that step's security fix. Nothing cumulative, nothing to scroll past. That diff is the lesson.

```
main                                  the vulnerable baseline, your workspace
 └─ step-01-prompt-injection          + input guardrail, hardened system prompt
     └─ step-02-token-propagation     + OIDC on MCP, token forwarding, audit log
         └─ step-03-excessive-agency  + role-gated organizer tools
             └─ step-04-sensitive-disclosure   + output guardrail, role-filtered RAG
                 └─ step-05-observability      + OpenTelemetry, consumption limit
```

The pull requests are permanent teaching artifacts and are never merged. They carry the `reference/do-not-merge` label.

## Prerequisites

Make sure you have the following installed locally:

- [JDK 25](https://adoptium.net/) (you can use [SDKMAN!](https://sdkman.io) to install it)
- [IntelliJ IDEA](https://www.jetbrains.com/idea/) or [VS Code](https://code.visualstudio.com/) with the Java and Quarkus extension
- [Podman Desktop](https://podman-desktop.io) or [Docker Desktop](https://www.docker.com/products/docker-desktop/) - **required** for the Postgres and Keycloak dev services that the apps start automatically
- [Ollama](https://ollama.com)
- [Quarkus CLI](https://quarkus.io/guides/cli-tooling) (optional but handy)

### Initial setup

Clone the repository and warm the dependency cache. This is slow the first time because Maven downloads the Quarkus BOM and all extensions:

```shell
./mvnw install -DskipTests
```

Then pull the default Ollama model. The first download is about 1 GB and is the one slow gate in the whole workshop - start it now and let it run in the background:

```shell
ollama pull qwen3.5:0.8b
```

## Two applications, two ports

The workshop needs **two** Quarkus applications running at the same time:

| App | Port | What it does |
| --- | ---- | ------------ |
| `conference-assistant` | 8080 | The AI agent. WebSocket chat UI at `http://localhost:8080`. OIDC-protected, RAG-enabled. |
| `conference-mcp-server` | 8081 | Exposes conference data tools over MCP. From step-02 onward it is OIDC-protected. |

Start the MCP server first so its Postgres and Keycloak dev service containers are up before the agent tries to connect. Both use Quarkus dev mode, which gives you hot reload, continuous testing, and the Dev UI at `/q/dev-ui`:

```shell
# Terminal 1 - start the MCP server
cd conference-mcp-server && ./mvnw quarkus:dev

# Terminal 2 - start the agent
cd conference-assistant && ./mvnw quarkus:dev
```

Open [http://localhost:8080](http://localhost:8080) in your browser. A chat widget appears in the bottom-right corner. Log in as **alice** (password: alice) and start chatting.

> [!NOTE]
> From step-02 onward both apps share a Keycloak dev service container. Start the MCP server first so Keycloak is already running when the agent starts.

> [!NOTE]
> The first Ollama model download (`qwen3.5:0.8b`, about 1 GB) is the slow gate. Let it finish before you expect responses from the chat.

## Logging in

Quarkus Dev Services starts Keycloak automatically in the default `quarkus` realm, with these identities defined in `application.properties` (`quarkus.keycloak.devservices.users.*` / `.roles.*`):

| Username | Password | Roles |
| -------- | -------- | ----- |
| alice | alice | attendee |
| carol | carol | attendee |
| dave | dave | attendee |
| bob | bob | attendee, organizer |

Password is the same as the username in every case. Roles are carried in the token's `groups` claim, which Quarkus reads by default.

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

## LLM provider

The default provider is **Ollama** with model `qwen3.5:0.8b`. This is a small (~1 GB), fast model that supports native tool calling, which the MCP tools in this workshop require.

To switch to a cloud provider, open `conference-assistant/src/main/resources/application.properties`, uncomment the block for your provider, comment out the Ollama block, and export the matching API key:

```shell
# OpenAI
export OPENAI_API_KEY=<your-key>

# Google Gemini (free tier available)
export GEMINI_API_KEY=<your-key>

# Anthropic
export ANTHROPIC_API_KEY=<your-key>
```

> [!NOTE]
> Small local models vary in how reliably they follow tool-calling instructions. If a model ignores a tool call or hallucinates a result, try again or switch to a larger model. The deterministic guardrail and authorization **tests** are the definitive proof that the security fixes work regardless of model behavior.

> [!WARNING]
> Keep API keys out of source control. You are responsible for any charges. Do not commit a provider switch to `main`.

### Slow model? Adjust the timeout

Local models can be slow. Both apps already set a generous timeout:

```properties
quarkus.langchain4j.timeout=1m
quarkus.langchain4j.ollama.timeout=1m
```

Raise it (for example `2m`) if a minute is not enough for your hardware.

## The path

Work through the steps in order. You stay on `main` the whole time: read the step's guide, exploit the vulnerability in your own running app, then apply the fix. The branch and its pull request are there when you want to check your work.

| Step | Topic | OWASP LLM | Guide | Branch |
| ---- | ----- | --------- | ----- | ------ |
| 1 | Prompt injection defense | LLM01 | [step-01.md](./docs/steps/step-01.md) | `step-01-prompt-injection` |
| 2 | Token propagation, object-level auth, audit logging | BOLA | [step-02.md](./docs/steps/step-02.md) | `step-02-token-propagation` |
| 3 | Excessive agency | LLM06 | [step-03.md](./docs/steps/step-03.md) | `step-03-excessive-agency` |
| 4 | Sensitive information disclosure | LLM02 | [step-04.md](./docs/steps/step-04.md) | `step-04-sensitive-disclosure` |
| 5 | Observability and unbounded consumption | LLM10 | [step-05.md](./docs/steps/step-05.md) | `step-05-observability` |

Each guide opens with a link to its pull request, where the diff shows the fix and nothing else.

> [!TIP]
> Exploit outcomes depend on the model and its mood. Do not be surprised if a smaller model refuses the attack spontaneously, or if a larger one needs more coaxing. What matters for correctness is the test suite: the guardrail and authorization tests run deterministically and prove the fix holds regardless of model behavior.

## Working with the step branches

### See what a step changed

Every step is one commit on top of the previous step, so a plain diff is the whole answer:

```shell
# what step-01 adds to the baseline
git diff main..step-01-prompt-injection

# what step-03 adds on top of step-02
git diff step-02-token-propagation..step-03-excessive-agency

# just the file list
git diff --stat step-02-token-propagation..step-03-excessive-agency
```

### Compare your own work against a reference

You are working on `main`, so once you have applied the step-01 fix yourself:

```shell
git diff step-01-prompt-injection
```

Anything that shows up is a difference between your solution and the reference. Some of it will be your own style, and that is fine.

### Run a reference step alongside your own work

Use a git worktree. It gives you a second checkout on disk without stashing, switching branches, or cloning again:

```shell
git worktree add ../ref-step-01 step-01-prompt-injection
cd ../ref-step-01/conference-mcp-server && ./mvnw quarkus:dev
```

When you are done:

```shell
git worktree remove ../ref-step-01
```

> [!IMPORTANT]
> The reference and your own copy both bind ports 8080 and 8081 and both want the same dev-service containers, so they cannot run at the same time. Stop your apps before starting the reference, or override `quarkus.http.port` in one of them.

## Further reading

- [Quarkus Documentation](https://quarkus.io/guides)
- [Quarkus LangChain4j Workshop](https://quarkus.io/quarkus-workshop-langchain4j/)
- [OWASP Top 10 for LLM Applications](https://owasp.org/www-project-top-10-for-large-language-model-applications/)
- [Getting ready for secure MCP with Quarkus MCP Server](https://quarkus.io/blog/secure-mcp-sse-server/)
- [Use Quarkus MCP client to access secure MCP HTTP servers](https://quarkus.io/blog/secure-mcp-client/)
- [Agentic AI with Quarkus - part 1](https://quarkus.io/blog/agentic-ai-with-quarkus/)
