# DocuMind (Java) — Microservices RAG Document Q&A

A faithful Java/Spring port of `documind_python`: upload PDFs and ask
natural-language questions, with answers **grounded** in your documents,
**cited**, and streamed token-by-token. Same architecture, same API, same React UI —
implemented with the latest Spring stack.

| Folder | Role | Port |
|---|---|---|
| `gateway/` | Public entry point — JWT auth, CORS, Redis rate limit, reactive reverse proxy (SSE pass-through) | 8080 |
| `document-service/` | Uploads + async Kafka ingestion (extract → chunk → embed → store) | 8001 |
| `query-service/` | RAG ask flow (hybrid retrieval, guardrails, grounded **SSE** answer) + Mongo history | 8002 |
| `libs/documind-contracts/` | Shared DTOs + Kafka events (the cross-service contract) | — |
| `libs/documind-common/` | pgvector store, hybrid retrieval + RRF, correlation-id filter | — |
| `frontend/` | React 18 + TS + Vite + TanStack Query + shadcn/ui — **identical to documind_python** | 5173 |

## Stack (latest)
Java 21 · Spring Boot 3.5 · **Spring Cloud Gateway** (reactive) · **Spring AI** (OpenAI / Anthropic / Ollama) · **Spring Kafka** (retry + DLT) · Spring Data **JPA** + **MongoDB** · reactive **Redis** · **jjwt** + BCrypt · **pgvector** · Apache **PDFBox**.

## Quick start
```bash
docker compose up --build
#   frontend  http://localhost:5173      gateway  http://localhost:8080
#   login:    demo / demo12345
```
Set a provider key first (or use Ollama, below):
```bash
# PowerShell:  $env:OPENAI_API_KEY = "sk-..."
export OPENAI_API_KEY=sk-...
```

### Free / local LLM (Ollama)
```bash
docker compose --profile ollama up -d ollama
docker compose exec ollama ollama pull qwen2.5:1.5b
docker compose exec ollama ollama pull nomic-embed-text
# then run the stack with the local provider:
CHAT_PROVIDER=ollama EMBEDDING_PROVIDER=ollama docker compose up --build
```

## Provider switch — one pair of env vars
`CHAT_PROVIDER` and `EMBEDDING_PROVIDER` ∈ `openai | ollama | anthropic` (Spring AI's
`spring.ai.model.*` selector). Anthropic has no embeddings API, so pair it with
`EMBEDDING_PROVIDER=openai`. The `embedding` column is **dimensionless**, so any
provider's vector size works with no migration (see `init-scripts/01-init.sql`).

## How it maps to documind_python
| documind_python | documind_java |
|---|---|
| FastAPI + Pydantic | Spring Boot Web + records + Bean Validation |
| FastAPI gateway (httpx SSE proxy) | Spring Cloud Gateway (reactive, native SSE) |
| LangChain `PGVector` / providers | `documind-common` JdbcTemplate + Spring AI `ChatModel`/`EmbeddingModel` |
| pgvector + Postgres FTS + RRF | same, in `PgVectorStore` + `Rrf` |
| aiokafka producer/consumer + DLT | Spring Kafka + `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` |
| Motor (Mongo) | Spring Data MongoDB |
| PyJWT + bcrypt + Redis limit | jjwt + BCrypt + reactive Redis filter |
| structlog + X-Request-ID | SLF4J/MDC + `CorrelationIdFilter` |
| SSE `astream` tokens | Spring AI `ChatClient.stream()` → `SseEmitter` |

## Build / run locally (without Docker)
```bash
mvn install                 # builds all 5 modules
# infra: docker compose up -d postgres mongodb kafka redis
mvn -pl gateway spring-boot:run
mvn -pl document-service spring-boot:run
mvn -pl query-service spring-boot:run
```

## Notes
- `backend/` is the **previous monolith** (kept for reference); the microservices above supersede it.
- `frontend.old/` is the pre-redesign UI (backup); `frontend/` is the current Apple-style UI.
- At scale: pin one embedding provider, `ALTER` the `embedding` column to `vector(N)`, and add an HNSW index for ANN search.
