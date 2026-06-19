# DocuMind (Java) — Full-Stack RAG Document Q&A

A full-stack RAG application: upload PDFs and ask natural-language questions about them, with answers grounded in your documents and rendered with source citations.

This repository is a monorepo with two parts:

| Folder | Stack | Runs on |
|---|---|---|
| [`backend/`](backend) | Java 21 · Spring Boot · Spring AI (OpenAI) · Kafka · PostgreSQL/pgvector · MongoDB | http://localhost:8080 |
| [`frontend/`](frontend) | React 18 · TypeScript · Vite · TanStack Query · Tailwind | http://localhost:5173 |

## Quick start

```bash
# 1. Backend — infra + API
cd backend
docker compose up -d                 # Postgres+pgvector, MongoDB, Kafka
export OPENAI_API_KEY=sk-...          # PowerShell: $env:OPENAI_API_KEY = "sk-..."
mvn spring-boot:run                   # http://localhost:8080  (Swagger at /swagger-ui.html)

# 2. Frontend — UI (in a second terminal)
cd frontend
npm install
cp .env.example .env                  # VITE_API_BASE_URL defaults to http://localhost:8080
npm run dev                           # http://localhost:5173
```

The backend exposes `POST /api/documents`, `GET /api/documents`, `POST /api/ask`
(returns a single grounded JSON answer with citations), and
`GET /api/conversations/{id}`. See [`backend/README.md`](backend/README.md) for the
architecture diagram, curl examples, demo script, and the
[learning guide](backend/LEARNING_GUIDE.md).

> A second, independent implementation of the same product on a Python/React
> stack (FastAPI + LangChain) lives in the separate `documind_python` repo.
