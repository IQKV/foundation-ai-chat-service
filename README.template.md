# Foundation AI Chat Service 💬

Spring AI integration template on the iQ Key Value platform. Demonstrates how to wire a multi-tenant, JWT-secured chat endpoint to a local Ollama instance within the standard foundation microservice layout. Works with any OpenAI-compatible provider via config.

## About

The AI Chat service provides a production-ready starting point for LLM-backed conversational features:

- **Spring AI Integration** — chat client pre-wired to a local Ollama instance by default; swap to any OpenAI-compatible provider via `spring.ai.*` config with no code changes
- **Streaming & Blocking Modes** — supports both blocking response and server-sent event (SSE) streaming out of the box
- **Multi-Tenant Isolation** — tenant context resolved from `X-Tenant-ID` header or JWT claim before every request; each tenant's conversation history is fully isolated
- **Conversation Persistence** — chat sessions and message history stored in PostgreSQL via MyBatis; Liquibase manages per-tenant schema migrations
- **Security** — stateless JWT resource server (RS256); public chat endpoints can be exposed per tenant while admin endpoints require `PLATFORM_ADMIN` authority
- **Audit Integration** — publishes `AuditEvent` messages to `iqkv.events` for sensitive actions (session creation, model override)

## Quick Links

- [API Documentation](./docs/api/README.md)
- [Architecture Overview](./docs/architecture/README.md)
- [Deployment Guide](./docs/deployment/README.md)
- [Contributing Guidelines](.github/CONTRIBUTING.md)

## API

Base path: `/api/v1/aichat`

### Chat

| Method   | Path                    | Auth | Description                                             |
| :------- | :---------------------- | :--- | :------------------------------------------------------ |
| `POST`   | `/chat`                 | JWT  | Send a message; returns a blocking completion response. |
| `POST`   | `/chat/stream`          | JWT  | Send a message; returns an SSE stream of token chunks.  |
| `GET`    | `/sessions`             | JWT  | List chat sessions for the authenticated user.          |
| `GET`    | `/sessions/{sessionId}` | JWT  | Get full message history for a session.                 |
| `DELETE` | `/sessions/{sessionId}` | JWT  | Delete a session and its message history.               |

### Admin

| Method | Path              | Auth                 | Description                                     |
| :----- | :---------------- | :------------------- | :---------------------------------------------- |
| `GET`  | `/admin/sessions` | JWT `PLATFORM_ADMIN` | Search sessions across all tenants (paginated). |
| `GET`  | `/admin/usage`    | JWT `PLATFORM_ADMIN` | Aggregate token usage statistics per tenant.    |

> Auth legend: `JWT` = valid Bearer token required; `JWT PLATFORM_ADMIN` = platform administrator authority required.

## Tech Stack

- Java 25 / Spring Boot 4.x
- Spring AI with Ollama (default; switchable to any OpenAI-compatible provider)
- MyBatis 3.x (no JPA) + PostgreSQL 17
- Liquibase for schema migrations
- RabbitMQ for async messaging and audit event publishing
- Spring Security + OAuth2 Resource Server (RS256 JWT)
- Micrometer + Prometheus

## Observability

- **Custom Metrics**:
    - `aichat.request.count`: Rate of chat requests by tenant and model.
    - `aichat.session.duration`: Chat session lifetime distribution.

## Prerequisites

- JDK 25 (Eclipse Temurin)
- Maven 3.9+
- Node.js >= 22.15.0 & pnpm >= 10.33.2 (git hooks)
- Docker & Docker Compose
- [Ollama](https://ollama.com) running locally with at least one model pulled (e.g. `ollama pull llama3.2`)

## Quick Start

```bash
# Clone the repository
git clone https://github.com/IQKV/foundation-ai-chat-service.git
cd foundation-ai-chat-service

# Install git hooks
pnpm install

# Pull a model into Ollama (one-time)
ollama pull llama3.2

# Copy environment variables
cp .env.example .env.local
# Defaults point to Ollama on localhost:11434 — no edits needed for first-run

# Start infrastructure dependencies (PostgreSQL, RabbitMQ, MailHog)
docker compose up -d

# Run the service
./mvnw spring-boot:run -Pdev
# → API:      http://localhost:8080
# → Actuator: http://localhost:8081/actuator/health
# → Swagger:  http://localhost:8080/swagger-ui.html
```

## Environment Variables

| Variable                              | Default                  | Description                                         |
| :------------------------------------ | :----------------------- | :-------------------------------------------------- |
| `DB_HOST`                             | `localhost`              | PostgreSQL host                                     |
| `DB_PORT`                             | `5432`                   | PostgreSQL port                                     |
| `DB_NAME`                             | `aichat`                 | Database name                                       |
| `DB_USERNAME`                         | `svc_aichat_dba`         | Database user                                       |
| `DB_PASSWORD`                         | `svc_aichat_dba`         | Database password                                   |
| `RABBITMQ_HOST`                       | `localhost`              | RabbitMQ host                                       |
| `RABBITMQ_PORT`                       | `5672`                   | RabbitMQ AMQP port                                  |
| `RABBITMQ_USERNAME`                   | `svc_aichat_rmq`         | RabbitMQ user                                       |
| `RABBITMQ_PASSWORD`                   | `svc_aichat_rmq`         | RabbitMQ password                                   |
| `MAIL_HOST`                           | `localhost`              | SMTP host                                           |
| `MAIL_PORT`                           | `1025`                   | SMTP port (MailHog default)                         |
| `MAIL_FROM`                           | `noreply@iqkv.dev`       | Sender address                                      |
| `SPRING_PROFILES_ACTIVE`              | `local`                   | Active Spring profile                                             |
| `SPRING_AI_OLLAMA_BASE_URL`           | `http://localhost:11434`  | Ollama server URL                                                 |
| `SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL` | `llama3.2`                | Model to use (must be pulled in Ollama first)                     |
| `ROLLOUT_MODE`                        | `MULTI_TENANT`            | Platform rollout mode                                             |

> Copy `.env.example` to `.env.local` / `.env.uat` / `.env.prd` and fill in values per environment. The defaults point to a local Ollama instance — no API key needed for local development. To switch to OpenAI or another provider, replace the `SPRING_AI_OLLAMA_*` variables with the corresponding `SPRING_AI_OPENAI_*` ones and add `SPRING_AI_OPENAI_API_KEY`.

## Maven Commands

```bash
# Build and test (skip Checkstyle during development)
./mvnw clean verify -Dcheckstyle.skip=true

# Run tests only
./mvnw test -Dcheckstyle.skip=true

# Explicit Checkstyle check
./mvnw checkstyle:check

# Coverage report → target/site/jacoco/index.html
./mvnw jacoco:report

# Production build
./mvnw clean package -Pproduction
```

## Docker

```bash
# Build image
docker build -t iqkv/foundation-ai-chat-service:latest .

# Run with full platform stack
docker compose -f compose.container.yaml up -d
```

## Monitoring

| Endpoint                   | Description                      |
| :------------------------- | :------------------------------- |
| `GET /actuator/health`     | Liveness + readiness probes      |
| `GET /actuator/info`       | Build info and platform metadata |
| `GET /actuator/metrics`    | Application metrics              |
| `GET /actuator/prometheus` | Prometheus scrape endpoint       |
| `GET /swagger-ui.html`     | API documentation                |

## Project Structure

```
src/main/java/com/iqkv/foundation/aichatservice/
├── chat/             # Core Bounded Context: sessions, messages, LLM orchestration
│   ├── domain/       # ChatSession, ChatMessage entities; ChatStore port
│   ├── application/  # ChatService (use cases)
│   └── adapter/
│       ├── in/rest/  # ChatRestResource, AdminChatRestResource
│       └── out/persistence/ # MyBatis implementation of ChatStore
├── infrastructure/   # Technical concerns: Spring AI config, RabbitMQ, Security, MyBatis
├── shared/           # Common exceptions, utilities, value objects
└── AiChatServiceApplication.java
```

## License

This project is licensed under the Apache License. See the [LICENSE](LICENSE) file for details.

## Contributing

Please read our [Contributing Guidelines](.github/CONTRIBUTING.md) and [Code of Conduct](.github/CODE_OF_CONDUCT.md).

---

## 🧩 Architecture

- **Spring AI**: Ollama chat client configured via `spring.ai.ollama.*`; model is overridable per request; swap to any OpenAI-compatible provider by changing the auto-configuration dependency and `spring.ai.*` properties — no code changes needed
- **Persistence**: MyBatis with XML mappers + PostgreSQL; Liquibase manages per-tenant schema migrations; `demo` context seeds example sessions in local/sit/uat
- **Messaging**: RabbitMQ publisher for `AuditEvent` messages; `iqkv.messaging.rabbitmq.enabled` toggle — disabled in base profile, enabled per environment
- **Security**: Spring Security + OAuth2 Resource Server; RS256 JWT validated via public key or JWKS URI; `@PreAuthorize` on every endpoint; tenant context resolved before request handling
- **Multi-tenancy**: `ROLLOUT_MODE` (`MULTI_TENANT` | `SINGLE_TENANT`) — must be identical across all platform services
- **Observability**: Micrometer + Prometheus; structured JSON logging with Logstash encoder; health probes for Kubernetes readiness/liveness
- **Quality Tools**: Checkstyle, JaCoCo (60% gate), ArchUnit, commit convention enforcement

> See [AGENTS.md](AGENTS.md) for repository structure, DDD patterns, and agent guidelines.
