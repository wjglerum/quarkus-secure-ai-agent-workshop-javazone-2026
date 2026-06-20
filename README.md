# Securing AI Agents with Quarkus and LangChain4j

In this workshop you get a **complete conference assistant** that already works end to end. The catch: it is deliberately insecure. Your job is to attack it, understand why each vulnerability exists, and then apply the fixes. The pattern repeats four times, one security topic per module, and takes roughly two hours in total.

The four topics follow the OWASP Top 10 for LLM Applications:

- **Prompt Injection** (LLM01) - an over-trusting system prompt and no input guardrail
- **Broken Object-Level Authorization** (LLM01 / BOLA) - an unauthenticated MCP server with a confused-deputy flaw
- **Excessive Agency** (LLM06) - privileged organizer tools accessible by any authenticated user
- **Sensitive Information Disclosure** (LLM02) - internal documents in the RAG corpus with no output guardrail

Each step is a self-contained reference solution that layers one fix on top of the previous one. You harden your own copy (step-00) by reading the numbered steps and applying the changes there. If you get stuck you can always diff your workspace against the reference.

## Prerequisites

Make sure you have the following installed locally:

- [JDK 25](https://adoptium.net/) (you can use [SDKMAN!](https://sdkman.io) to install it)
- [IntelliJ IDEA](https://www.jetbrains.com/idea/) or [VS Code](https://code.visualstudio.com/) with the Java and Quarkus extension
- [Podman Desktop](https://podman-desktop.io) or [Docker Desktop](https://www.docker.com/products/docker-desktop/) - **required** for the Postgres and Keycloak dev services that the apps start automatically
- [Ollama](https://ollama.com)
- [Quarkus CLI](https://quarkus.io/guides/cli-tooling) (optional but handy)

### Initial setup

Clone the repository and warm the dependency cache for all modules. This is slow the first time because Maven downloads the Quarkus BOM and all extensions:

```shell
./mvnw install -DskipTests
```

Then pull the default Ollama model. The first download is about 1 GB and is the one slow gate in the whole workshop - start it now and let it run in the background:

```shell
ollama pull qwen3.5:0.8b
```

## Two applications, two ports

Each step directory contains **two** Quarkus applications that must run at the same time:

| App | Port | What it does |
| --- | ---- | ------------ |
| `conference-assistant` | 8080 | The AI agent. WebSocket chat UI at `http://localhost:8080`. OIDC-protected, RAG-enabled. |
| `conference-mcp-server` | 8081 | Exposes conference data tools over MCP. From step-02 onward it is OIDC-protected. |

Start the MCP server first, then the agent. Both use Quarkus dev mode, which gives you hot reload, continuous testing, and the Dev UI at `/q/dev-ui`:

```shell
# Terminal 1 - start the MCP server
cd step-00-your-workspace/conference-mcp-server && ../../mvnw quarkus:dev

# Terminal 2 - start the agent
cd step-00-your-workspace/conference-assistant && ../../mvnw quarkus:dev
```

Open [http://localhost:8080](http://localhost:8080) in your browser. A chat widget appears in the bottom-right corner.

> [!NOTE]
> From step-02 onward both apps share a Keycloak dev service container. Start the MCP server first so Keycloak is already running when the agent starts.

## Logging in

Quarkus Dev Services starts Keycloak automatically. The realm is pre-seeded with these identities:

| Username | Password | Role |
| -------- | -------- | ---- |
| alice | alice | attendee |
| carol | carol | attendee |
| dave | dave | attendee |
| bob | bob | organizer |

Password is the same as the username in every case.

## LLM provider

The default provider is **Ollama** with model `qwen3.5:0.8b`. This is a small (~1 GB), fast model that supports native tool calling, which the MCP tools in this workshop require.

To switch to a cloud provider, open `application.properties` in the `conference-assistant` for the step you are running, uncomment the block for your provider, and export the matching API key:

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
> Keep API keys out of source control. You are responsible for any charges.

## The path

Work through the modules in order. `step-00-your-workspace` is your personal workspace: you run the app there and apply all your hardening changes there. The numbered steps are cumulative reference solutions - each one is the full working solution up to and including that security fix.

| Step | Topic | OWASP LLM | Instructions |
| ---- | ----- | --------- | ------------ |
| [step-00-your-workspace](./step-00-your-workspace/README.md) | Your workspace - start here | n/a | Explore the vulnerable app |
| [step-01-prompt-injection](./step-01-prompt-injection/README.md) | Prompt injection defense | LLM01 | Input guardrail + hardened system prompt |
| [step-02-token-propagation](./step-02-token-propagation/README.md) | Token propagation and object-level auth | LLM01 / BOLA | OIDC on MCP server, token forwarding, identity-derived access |
| [step-03-excessive-agency](./step-03-excessive-agency/README.md) | Excessive agency | LLM06 | Role-gated organizer tools |
| [step-04-sensitive-disclosure](./step-04-sensitive-disclosure/README.md) | Sensitive information disclosure | LLM02 | Output guardrail + scoped RAG corpus |

> [!TIP]
> Exploit outcomes depend on the model and its mood. Do not be surprised if a smaller model refuses the attack spontaneously, or if a larger one needs more coaxing. What matters for correctness is the test suite: the guardrail and authorization tests run deterministically and prove the fix holds regardless of model behavior.

## Further reading

- [Quarkus Documentation](https://quarkus.io/guides)
- [Quarkus LangChain4j Workshop](https://quarkus.io/quarkus-workshop-langchain4j/)
- [OWASP Top 10 for LLM Applications](https://owasp.org/www-project-top-10-for-large-language-model-applications/)
- [Getting ready for secure MCP with Quarkus MCP Server](https://quarkus.io/blog/secure-mcp-sse-server/)
- [Use Quarkus MCP client to access secure MCP HTTP servers](https://quarkus.io/blog/secure-mcp-client/)
- [Agentic AI with Quarkus - part 1](https://quarkus.io/blog/agentic-ai-with-quarkus/)
