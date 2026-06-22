# DocuMind — RAG Document Q&A Service

DocuMind is a Spring Boot service that lets you upload PDF documents and ask natural-language questions about them. Uploads are ingested asynchronously through Kafka — text is extracted with PDFBox, split into overlapping chunks, embedded with OpenAI's `text-embedding-3-small`, and stored in PostgreSQL/pgvector. Questions are answered with retrieval-augmented generation (RAG): the question is embedded, the most similar chunks are retrieved by cosine similarity, and `gpt-4o-mini` answers strictly from that context with `[filename, chunk N]` citations. Conversation history lives in MongoDB. The chat model is provider-pluggable via Spring AI — switching to Anthropic Claude is a config-and-dependency change with zero Java changes.

## Architecture

```
 FLOW 1: UPLOAD & INGESTION (asynchronous)
 ==========================================

  Client                DocuMind API                          Kafka                 Ingestion Consumer
    |                        |                                  |                          |
    |--POST /api/documents-->|                                  |                          |
    |    (multipart PDF)     |--save file ./storage/{id}.pdf    |                          |
    |                        |--insert metadata (UPLOADED)----------------> [Postgres]     |
    |                        |--publish DocumentUploadedEvent-->|                          |
    |<--202 + documentId-----|                                  |--document-events-------->|
    |                        |                                  |                          |--status=PROCESSING
    |                        |                                  |                          |--PDFBox extract text
    |                        |                                  |                          |--chunk (~800 tok, 100 overlap)
    |                        |                                  |                          |--embed batches of 20 ----> [OpenAI embeddings]
    |                        |                                  |                          |--insert chunks+vectors --> [Postgres/pgvector]
    |                        |                                  |                          |--status=READY
    |                        |                                  |    on repeated failure:  |
    |                        |                                  |<--document-events.DLT----|--status=FAILED


 FLOW 2: ASK (synchronous RAG)
 ==========================================

  Client                DocuMind API
    |                        |
    |--POST /api/ask-------->|
    |   {question}           |--embed question -----------------------> [OpenAI embeddings]
    |                        |--top-4 cosine similarity search -------> [Postgres/pgvector]
    |                        |--grounded prompt (context + rules) ----> [gpt-4o-mini]
    |                        |--persist Q&A turn ---------------------> [MongoDB]
    |<--{answer, citations,--|
    |    conversationId}     |
```

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop (for Postgres+pgvector, MongoDB, Kafka)
- An OpenAI API key with a few dollars of credit ([platform.openai.com](https://platform.openai.com))

## Run it

```bash
# 1. Infrastructure (Postgres+pgvector, MongoDB, Kafka in KRaft mode)
docker compose up -d

# 2. API key
export OPENAI_API_KEY=sk-...            # macOS/Linux
#   PowerShell:  $env:OPENAI_API_KEY = "sk-..."

# 3. Run the app (Kafka topics are created automatically at startup)
mvn spring-boot:run
```

Swagger UI: <http://localhost:8080/swagger-ui.html>

> Windows note: in PowerShell, `curl` is an alias for `Invoke-WebRequest` — use `curl.exe` for the examples below, or use Git Bash / Swagger UI.

## API

**Upload a PDF** (max 20 MB; returns 202, ingestion is async):

```bash
curl -X POST http://localhost:8080/api/documents -F "file=@mydoc.pdf"
```

```json
{ "documentId": "9b2f...", "status": "UPLOADED", "message": "Document accepted for processing" }
```

**List documents** (poll until `status` is `READY`):

```bash
curl http://localhost:8080/api/documents
```

**Ask a question** (optionally scoped with `"documentId"`; rate-limited to 10/min per IP):

```bash
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the refund policy?"}'
```

```json
{
  "answer": "Refunds are issued within 30 days [mydoc.pdf, chunk 4].",
  "citations": [ { "filename": "mydoc.pdf", "chunkIndex": 4 } ],
  "conversationId": "0d1e..."
}
```

**Continue a conversation** — pass the returned id back:

```bash
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "And for digital goods?", "conversationId": "0d1e..."}'
```

**Conversation history**:

```bash
curl http://localhost:8080/api/conversations/0d1e...
```

## 5-step demo script

1. `docker compose up -d` then `mvn spring-boot:run` (with `OPENAI_API_KEY` exported) — point out the auto-created Kafka topics in the startup log.
2. Upload any PDF: `curl -X POST http://localhost:8080/api/documents -F "file=@mydoc.pdf"` — note the instant 202 while the log shows `stage=consume → stage=chunked → stage=embedded → stage=ready`.
3. `curl http://localhost:8080/api/documents` — show the status now `READY` with a real `chunkCount`.
4. Ask something the document answers — show the grounded answer and its citations; then ask something it *cannot* answer — show the exact "I don't have enough information in the uploaded documents." response (hallucination control, live).
5. `curl http://localhost:8080/api/conversations/{id}` — show the full turn history persisted in MongoDB.

## Switching the chat model to Claude

Anthropic has no embeddings API, so embeddings stay on OpenAI — but the chat model swaps with zero Java changes, because `AskService` depends only on Spring AI's `ChatModel` interface:

1. In `pom.xml`, uncomment the `spring-ai-starter-model-anthropic` dependency.
2. In `application.yml`, uncomment the `spring.ai.model.chat: anthropic` block (it pins embeddings to OpenAI and selects Claude for chat).
3. `export ANTHROPIC_API_KEY=...` and restart.

## Project layout

```
src/main/java/com/org/documind/
├── controller/   DocumentController, AskController, ConversationController
├── service/      DocumentService, IngestionService, AskService, TextChunker,
│                 PdfTextExtractor, ConversationService, RateLimitService
├── repository/   DocumentRepository, DocumentChunkRepository (+ChunkMatch,
│                 VectorLiterals), ConversationTurnRepository
├── kafka/        DocumentEventProducer, DocumentEventConsumer
├── config/       DocuMindProperties, KafkaConfig, WebConfig,
│                 RateLimitInterceptor, OpenApiConfig
├── dto/          requests/responses + DocumentUploadedEvent + Citation
├── entity/       DocumentEntity, DocumentChunkEntity, ConversationTurn
└── exception/    GlobalExceptionHandler + domain exceptions
```

See [LEARNING_GUIDE.md](LEARNING_GUIDE.md) for the full walkthrough, deep dives, and interview prep.
