## 📜 API Documentation

Base path: `/api/v1/aichat`

All endpoints require a valid RS256 JWT issued by the IAM service unless marked as public.
The JWT must be passed as a `Bearer` token in the `Authorization` header.

---

### Ping (Reachability Probes)

| Method | Path              | Auth                 | Description                                                           |
| ------ | ----------------- | -------------------- | --------------------------------------------------------------------- |
| `GET`  | `/ping`           | public               | Unauthenticated reachability probe — no JWT or tenant header required |
| `GET`  | `/tenant/ping`    | JWT + `X-Tenant-ID`  | Tenant-scoped probe — echoes resolved tenant key                      |
| `GET`  | `/admin/ping`     | JWT `PLATFORM_ADMIN` | Admin probe — echoes caller's `user_id` claim                         |

`GET /ping` is suitable for infrastructure health checks and smoke tests that run before auth is available.

`GET /tenant/ping` verifies that tenant resolution (from `X-Tenant-ID` header or JWT `tenant_id` claim) is working correctly.

`GET /admin/ping` confirms the caller's JWT is decoded correctly and carries the `PLATFORM_ADMIN` authority.

---

### Chat

| Method   | Path                                 | Auth | Description                                                         |
| -------- | ------------------------------------ | ---- | ------------------------------------------------------------------- |
| `POST`   | `/chat`                              | JWT  | Send a message; creates a new session if `sessionId` is omitted     |
| `GET`    | `/chat/sessions`                     | JWT  | List the authenticated user's chat sessions (paginated)             |
| `GET`    | `/chat/sessions/{sessionId}/messages` | JWT  | Get message history for a session (paginated)                       |
| `DELETE` | `/chat/sessions/{sessionId}`         | JWT  | Delete a session and all its messages                               |

#### POST /chat — Send a message

Request body:

```json
{
  "sessionId": "6f54d678-4830-43fd-ab0a-314f8f445885",
  "content": "What is Spring AI?"
}
```

- `sessionId` — optional. Omit to start a new conversation; include to continue an existing one.
- `content` — required. Maximum **4 000 characters** (enforced by both the frontend and the backend).

Response:

```json
{
  "sessionId": "6f54d678-4830-43fd-ab0a-314f8f445885",
  "sessionTitle": "What is Spring AI?",
  "reply": "Spring AI is a framework ...",
  "model": "llama3.1:8b",
  "timestamp": "2026-10-06T12:30:46.678684815Z"
}
```

- `sessionId` — the session UUID (newly created or existing).
- `sessionTitle` — set from the first 60 characters of the first user message.
- `reply` — the LLM's response. May take up to 3 minutes for long generations.
- `model` — the Ollama model that produced the reply.

> **Timeout note:** LLM inference can take 60–180 seconds depending on model and prompt length. The gateway route has a 180-second response timeout configured to accommodate this.

#### GET /chat/sessions — List sessions

Query parameters:

| Parameter | Type    | Default | Description          |
| --------- | ------- | ------- | -------------------- |
| `limit`   | integer | 20      | Page size            |
| `offset`  | integer | 0       | Zero-based offset    |

Response:

```json
{
  "items": [
    {
      "id": "6f54d678-4830-43fd-ab0a-314f8f445885",
      "userId": "a1b2c3d4-...",
      "title": "What is Spring AI?",
      "model": "llama3.1:8b",
      "createdAt": "2026-10-06T12:00:00Z",
      "updatedAt": "2026-10-06T12:30:46Z"
    }
  ],
  "totalElements": 1
}
```

Sessions are ordered by `updated_at DESC` — most recent conversation first.

#### GET /chat/sessions/{sessionId}/messages — Get message history

Query parameters: `limit` (default 50), `offset` (default 0).

Response:

```json
{
  "items": [
    {
      "id": "msg-uuid",
      "sessionId": "6f54d678-...",
      "role": "USER",
      "content": "What is Spring AI?",
      "createdAt": "2026-10-06T12:30:40Z"
    },
    {
      "id": "msg-uuid-2",
      "sessionId": "6f54d678-...",
      "role": "ASSISTANT",
      "content": "Spring AI is a framework ...",
      "createdAt": "2026-10-06T12:30:46Z"
    }
  ],
  "totalElements": 2
}
```

Message `role` values: `USER`, `ASSISTANT`, `SYSTEM`.

Only the session owner can read their session's messages. Attempts to access another user's session return `404`.

#### DELETE /chat/sessions/{sessionId} — Delete session

Returns `204 No Content`. Deleting a session cascades to all its messages (`ON DELETE CASCADE`).
Only the session owner can delete their own session.

---

### Admin

| Method | Path                      | Auth                 | Description                                       |
| ------ | ------------------------- | -------------------- | ------------------------------------------------- |
| `GET`  | `/admin/sessions`         | JWT `PLATFORM_ADMIN` | List all sessions across all users (paginated)    |

Query parameters: `limit` (default 20), `offset` (default 0).

Admin endpoints are cross-user — no `X-Tenant-ID` or ownership check applied.

---

### Error Responses

All errors follow RFC 7807 `ProblemDetail` format:

```json
{
  "type": "about:blank",
  "title": "LLM Backend Error",
  "status": 502,
  "detail": "HTTP 404 - {\"error\":\"model 'llama3.2' not found\"}",
  "instance": "/api/v1/aichat/chat",
  "correlationId": "abc12345",
  "requestId": "req-a1b2c3d4"
}
```

| Status | When                                                             |
| ------ | ---------------------------------------------------------------- |
| 400    | Validation failure (missing content, content too long)           |
| 401    | Missing or invalid JWT                                           |
| 403    | Insufficient authority (e.g. accessing admin endpoints)          |
| 404    | Session not found or not owned by the caller                     |
| 502    | LLM backend error (Ollama unreachable, model not found, etc.)    |
| 500    | Unexpected internal error                                        |

---

### Prompt Engineering Controls

The system prompt, input limit, output token budget, and temperature are configurable via environment variables — no rebuild required:

| Variable            | Default  | Description                                           |
| ------------------- | -------- | ----------------------------------------------------- |
| `AI_SYSTEM_PROMPT`  | (built-in) | System message injected on every call               |
| `AI_MAX_INPUT_CHARS`| 4000     | User message truncation limit                         |
| `AI_MAX_OUTPUT_TOKENS` | 1024  | Maximum tokens the LLM may generate per response      |
| `AI_TEMPERATURE`    | 0.7      | Response creativity (0.0 = focused, 1.0 = creative)   |

---

### Interactive Documentation

Swagger UI is available at `http://localhost:8080/swagger-ui.html` when running locally.
The Gateway aggregates the AI Chat spec at `/api/v1/aichat/api-docs`.

> Auth legend: `public` = no token required; `JWT` = valid Bearer token; `JWT PLATFORM_ADMIN` = JWT with platform administrator authority.
