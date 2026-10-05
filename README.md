# Foundation AI Chat Service 🚀

GitHub template demonstrating Spring AI integration on the iQ Key Value platform. Extends the standard `foundation-microservice-project-layout` with a working chat feature backed by Ollama — use it as a starting point for services that need locally-hosted LLM-backed conversational capabilities.

## About

This template provides:

- **Maven single-module layout** — standard `src/main/java` structure with MyBatis, PostgreSQL, Liquibase, and RabbitMQ wired up
- **Spring AI integration** — chat endpoint wired to a local Ollama instance by default; swap to any OpenAI-compatible provider via `spring.ai.*` config
- **Multi-profile Spring config** — `local`, `sit`, `uat`, `prd` profiles with correct defaults per environment
- **Docker Compose** — `compose.base.yaml` with PostgreSQL, RabbitMQ, MailHog, SonarQube, Prometheus, and Grafana; `compose.container.yaml` for full runtime stack including the service container
- **Dockerfile** — multi-stage build with layered JAR extraction, non-root `appuser`, and JVM tuning
- **Database Initialization** — `docker/postgres/init-aichat.sql` for automated schema and extension setup
- **Security** — Spring Security + OAuth2 Resource Server (RS256 JWT) pre-configured
- **Observability** — Micrometer + Prometheus + structured JSON logging (Logstash encoder)
- **Quality tools** — Checkstyle, JaCoCo, ArchUnit, Husky git hooks, commitlint

## Template Usage

1. Click **[Use this template](https://github.com/IQKV/foundation-ai-chat-service/generate)** to create your repository
2. Replace all `aichat` / `AiChatService` tokens with your service name (see the token map in `foundation-microservice-project-layout`)
3. Update `pom.xml` — `artifactId`, `name`, `description`, `start-class`
4. Rename the Java package from `com.iqkv.foundation.aichatservice` to your package
5. Adjust or extend the Spring AI chat feature to fit your use case
6. Remove unused dependencies from `pom.xml` (e.g. `shedlock` if no scheduled jobs needed)

## Quick Links

- [API Documentation](./docs/api/README.md)
- [Architecture Overview](./docs/architecture/README.md)
- [Deployment Guide](./docs/deployment/README.md)
- [Contributing Guidelines](.github/CONTRIBUTING.md)

## Tech Stack

- Java 25 / Spring Boot (latest via parent POM)
- Spring AI with Ollama (default; switchable to any OpenAI-compatible provider)
- MyBatis 3.x (no JPA) + PostgreSQL 17
- Liquibase for schema migrations
- RabbitMQ for async messaging
- Spring Security + OAuth2 Resource Server (RS256 JWT)
- ShedLock for distributed scheduled jobs
- Micrometer + Prometheus
- springdoc-openapi (Swagger UI)

## Prerequisites

- JDK 25 (Eclipse Temurin)
- Maven 3.9+
- Node.js >= 22.15.0 & pnpm >= 10.33.2 (git hooks)
- Docker & Docker Compose
- [Ollama](https://ollama.com) running locally with at least one model pulled (e.g. `ollama pull llama3.2`)

## Quick Start

```bash
# Clone or use as template
git clone https://github.com/IQKV/foundation-ai-chat-service.git my-service
cd my-service

# Install git hooks
pnpm install

# Pull a model into Ollama (one-time)
ollama pull llama3.2

# Copy environment variables
cp .env.example .env.local
# Defaults point to Ollama on localhost:11434 — no API key needed for local dev

# Start dependencies (PostgreSQL, RabbitMQ, MailHog, etc.)
docker compose up -d

# Run the service
./mvnw spring-boot:run -Pdev
# → API:      http://localhost:8080
# → Actuator: http://localhost:8081/actuator/health
# → Swagger:  http://localhost:8080/swagger-ui.html
# → MailHog:  http://localhost:8025
```

## Environment Variables

| Variable                              | Default                  | Description                           |
| ------------------------------------- | ------------------------ | ------------------------------------- |
| `DB_HOST`                             | `localhost`              | PostgreSQL host                       |
| `DB_PORT`                             | `5432`                   | PostgreSQL port                       |
| `DB_NAME`                             | `aichat`                 | Database name                         |
| `DB_USERNAME`                         | `svc_aichat_dba`         | Database user                         |
| `DB_PASSWORD`                         | `svc_aichat_dba`         | Database password                     |
| `RABBITMQ_HOST`                       | `localhost`              | RabbitMQ host                         |
| `RABBITMQ_PORT`                       | `5672`                   | RabbitMQ AMQP port                    |
| `RABBITMQ_USERNAME`                   | `svc_aichat_rmq`         | RabbitMQ user                         |
| `RABBITMQ_PASSWORD`                   | `svc_aichat_rmq`         | RabbitMQ password                     |
| `MAIL_HOST`                           | `localhost`              | SMTP host (MailHog)                   |
| `MAIL_PORT`                           | `1025`                   | SMTP port                             |
| `MAIL_FROM`                           | `noreply@iqkv.dev`       | Default sender email                  |
| `SPRING_AI_OLLAMA_BASE_URL`           | `http://localhost:11434` | Ollama server URL                     |
| `SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL` | `llama3.2`               | Model name (must be pulled in Ollama) |
| `ROLLOUT_MODE`                        | `MULTI_TENANT`           | Platform rollout mode                 |

> Copy `.env.example` to `.env.local` / `.env.uat` / `.env.prd` and fill in values per environment.

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
docker build -t iqkv/aichat:latest .

# Run full stack (service + dependencies)
docker compose -f compose.container.yaml up -d
```

## Monitoring

| Endpoint                   | Description                 |
| -------------------------- | --------------------------- |
| `GET /actuator/health`     | Liveness + readiness probes |
| `GET /actuator/metrics`    | Application metrics         |
| `GET /actuator/prometheus` | Prometheus scrape endpoint  |
| `GET /swagger-ui.html`     | API documentation           |
| `GET /` (on port 8025)     | MailHog Web UI              |

## Project Structure

```
.
├── docker/
│   ├── postgres/       # DB initialization scripts
│   ├── prometheus/     # Prometheus configuration
│   └── grafana/        # Grafana dashboards and datasources
├── src/main/java/com/iqkv/foundation/aichatservice/
│   ├── {domain}/       # Feature module (vertical slice)
│   │   ├── {Entity}.java
│   │   ├── {Entity}Service.java
│   │   ├── {Entity}RestResource.java
│   │   └── dto/
│   ├── infrastructure/ # Spring config, security, MyBatis, RabbitMQ setup
│   └── shared/         # Common exceptions, utilities, value objects
```

## License

This project is licensed under the Apache License. See the [LICENSE](LICENSE) file for details.

## Branch Strategy

> **Template note:** The strategy below is the default shipped with this template. Replace it with whatever model fits your team — update branch names, CI triggers, and protection rules to match.

This template uses **scaled trunk-based development**:

- `dev` is the trunk — always stable and deployable
- Short-lived feature/bugfix branches merge into `dev` via pull request
- Releases are cut from `dev` as annotated tags (`v1.2.0`) — no separate release branch

```
dev  ← trunk
├── feature/*
├── bugfix/*
├── improvement/*
├── hotfix/*
└── rfc/*
```

## Contributing

Please read our [Contributing Guidelines](.github/CONTRIBUTING.md) and [Code of Conduct](.github/CODE_OF_CONDUCT.md).

---

## 🧩 Architecture

- **Spring AI**: Ollama chat client configured via `spring.ai.ollama.*`; model is overridable per request; swap to any OpenAI-compatible provider by changing the auto-configuration dependency and `spring.ai.*` properties — no code changes needed
- **Persistence**: MyBatis with XML mappers + PostgreSQL; Liquibase manages schema migrations; `demo` context for seed data in local/sit/uat
- **Messaging**: RabbitMQ consumer/publisher; `iqkv.messaging.rabbitmq.enabled` toggle — disabled in base, enabled per profile
- **Security**: Spring Security + OAuth2 Resource Server; RS256 JWT validated via public key; `@PreAuthorize` on every endpoint
- **Multi-tenancy**: `ROLLOUT_MODE` (`MULTI_TENANT` | `SINGLE_TENANT`) — must be identical across all platform services
- **Credential convention**: DB users follow `svc_{service}_dba`, RabbitMQ users follow `svc_{service}_rmq`
- **Observability**: Micrometer + Prometheus; structured JSON logging with Logstash encoder; health probes for Kubernetes
- **GitHub Integration**: Issue templates, labels, Dependabot, and CI workflows
- **Quality Tools**: Checkstyle, JaCoCo (60% gate), ArchUnit, commit convention enforcement

> See [AGENTS.md](AGENTS.md) for repository structure, DDD patterns, and agent guidelines.
