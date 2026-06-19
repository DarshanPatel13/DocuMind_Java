# DocuMind Learning Guide

This guide is how you *own* this project — not just run it. Read it top to bottom, then have Claude quiz you on Section 4 one question at a time.

---

## 1. Big picture

### What RAG is

A large language model knows only what was in its training data. It knows nothing about *your* PDFs, and when asked about things it doesn't know, it tends to produce fluent, confident, wrong answers — hallucinations. **Retrieval-Augmented Generation (RAG)** fixes this by splitting the problem in two: a *retrieval* system finds the passages of your documents most relevant to the question, and the LLM is then asked to answer **only from those passages**. The model stops being an oracle and becomes a reading-comprehension engine. You get answers grounded in your own data, with citations, without training or fine-tuning anything.

The key enabling trick is the **embedding**: a function that maps any piece of text to a point in high-dimensional space such that texts with similar *meaning* land close together. "What's the refund window?" and "Customers may return products within 30 days" share almost no words, but their embeddings are near neighbours. That's what makes retrieval semantic rather than keyword-based.

### Why this architecture exists

Every piece of this system is there for a reason you can defend:

- **Postgres + pgvector** stores chunks and their vectors — one boring, transactional database doing double duty as a vector store.
- **Kafka** decouples the upload request from the expensive ingestion work, and gives retries, backpressure, and a dead-letter queue for free.
- **MongoDB** holds conversation history — an append-only, read-it-all-back log, which is exactly what a document store is good at.
- **Spring AI** keeps the code provider-agnostic: services depend on `ChatModel` and `EmbeddingModel` interfaces; application.yml decides that OpenAI implements them today and Claude could tomorrow.

### Walkthrough 1: upload → ingestion

1. A client POSTs a PDF to `/api/documents`. **`DocumentController.upload`** hands the multipart file to **`DocumentService.upload`**.
2. `DocumentService` validates it: non-empty, ≤ 20 MB, `.pdf` name or content type, and — the part attackers care about — the first four bytes must literally be `%PDF`.
3. The file is copied to `./storage/{documentId}.pdf`, a **`DocumentEntity`** row is saved in Postgres with status `UPLOADED`, and **`DocumentEventProducer.publish`** sends a **`DocumentUploadedEvent`** (JSON) to the Kafka topic `document-events`, keyed by documentId. The API returns **202 Accepted** immediately — total request time: milliseconds.
4. **`DocumentEventConsumer.onDocumentUploaded`** (consumer group `documind-ingestion`) receives the event and calls **`IngestionService.ingest`**.
5. `IngestionService` first checks idempotency: if the document is already `READY`, it logs and returns (Kafka may redeliver events). Otherwise it sets status `PROCESSING`.
6. **`PdfTextExtractor.extract`** pulls the text out with PDFBox.
7. **`TextChunker.chunk`** splits it into ~3200-character windows (≈800 tokens) that overlap by ~400 characters (≈100 tokens).
8. `IngestionService` deletes any existing chunks for the document (partial-failure healing), then for each batch of up to 20 chunks calls **`EmbeddingModel.embed`** and inserts each chunk + vector via **`DocumentChunkRepository.insertChunk`** (a native SQL insert that casts the vector literal).
9. Status becomes `READY` with the chunk count. If any step throws, the **`DefaultErrorHandler`** in **`KafkaConfig`** retries 3 times with exponential backoff (1s, 2s, 4s); if it still fails, the recoverer marks the document `FAILED` with a reason and publishes the event to `document-events.DLT`.

### Walkthrough 2: ask → retrieval → answer

1. A client POSTs `{question, documentId?, conversationId?}` to `/api/ask`. **`RateLimitInterceptor`** runs first: over 10 requests this minute from this IP → 429.
2. **`AskController.ask`** validates the body (`@Valid` on **`AskRequest`**) and calls **`AskService.ask`**.
3. `AskService` embeds the question with **`EmbeddingModel.embed`** — the question is now a point in the same 1536-dimension space as every chunk.
4. **`DocumentChunkRepository.findNearest`** (or `findNearestInDocument` when a documentId was given) runs a native pgvector query: `ORDER BY embedding <=> :questionVector LIMIT 4` — the four chunks with the smallest cosine distance.
5. **Grounding guard:** if zero chunks come back, `AskService` returns the exact sentinel *"I don't have enough information in the uploaded documents."* without calling the LLM at all — no context means nothing legitimate to answer from.
6. Otherwise it builds the grounded prompt: a system message with the rules (answer only from context, cite as `[filename, chunk N]`, use the sentinel if the context falls short) and a user message containing the four labelled chunks plus the question. **`ChatModel.call`** sends it to gpt-4o-mini.
7. The answer, citations, and retrieved chunk ids are persisted as a **`ConversationTurn`** in MongoDB and returned as an **`AskResponse`**.
8. `GET /api/conversations/{id}` replays the whole exchange via **`ConversationService.history`**.

---

## 2. File-by-file walkthrough

**`DocuMindApplication`** — the Spring Boot entry point. `@ConfigurationPropertiesScan` activates the typed config record.

**config/`DocuMindProperties`** — an immutable record bound to the `documind.*` block of application.yml: storage dir, chunk sizes, topic names, top-K, rate limit. Exists so magic numbers live in exactly one place and arrive type-checked.

**config/`KafkaConfig`** — declares the two `NewTopic` beans (auto-created at startup) and the consumer error policy: `DefaultErrorHandler` with `ExponentialBackOffWithMaxRetries(3)`, whose recoverer marks the document FAILED and delegates to `DeadLetterPublishingRecoverer`. `DocumentNotFoundException` is registered as not-retryable — a missing row won't appear by waiting.

**config/`WebConfig` / `RateLimitInterceptor`** — registers the interceptor for `/api/ask` only. The interceptor resolves the client IP (X-Forwarded-For aware) and throws `RateLimitExceededException` when the budget is spent; the advice turns that into a 429 with `Retry-After`.

**config/`OpenApiConfig`** — title/description/version for Swagger UI.

**controller/`DocumentController`** — `POST /api/documents` (multipart → 202 + documentId) and `GET /api/documents`. Thin: delegates everything to the service.

**controller/`AskController`** — `POST /api/ask` with bean validation. Thin on purpose: testable logic belongs in `AskService`.

**controller/`ConversationController`** — `GET /api/conversations/{id}`.

**service/`DocumentService`** — upload validation (size, name/content-type, `%PDF` magic bytes), storage under a server-generated UUID (no client-controlled paths), metadata insert, event publish — in that order, so the event never references something that doesn't exist yet.

**service/`TextChunker`** — pure function from text to overlapping windows. Token counts are approximated as characters/4; the comments explain why an approximation is fine. The constructor rejects nonsensical config (overlap ≥ chunk size).

**service/`PdfTextExtractor`** — PDFBox behind a one-method seam, easy to mock and easy to replace with OCR.

**service/`IngestionService`** — orchestrates the pipeline with the idempotency guard, delete-then-reinsert healing, batched embedding calls, and stage-by-stage structured logging. Deliberately not one big transaction: API calls taking seconds must not pin a DB connection.

**service/`AskService`** — the RAG core; the five numbered steps in its `ask` method mirror Walkthrough 2 exactly. Holds the system prompt and the public `NO_INFO_ANSWER` sentinel.

**service/`ConversationService`** — history lookup; 404 via exception when the conversation doesn't exist.

**service/`RateLimitService`** — fixed-window counter per client key in a `ConcurrentHashMap`, atomic via `compute`. Per-instance by design; the comment says what changes in a cluster (Redis).

**repository/`DocumentRepository`** — standard Spring Data JPA plus a derived newest-first finder.

**repository/`DocumentChunkRepository`** — the most interesting persistence code: native `INSERT ... CAST(:embedding AS vector)` (Hibernate has no pgvector type, so vectors travel as `[0.1,0.2,...]` text literals) and the two similarity searches ordered by the `<=>` cosine-distance operator, returning **`ChunkMatch`** interface projections.

**repository/`VectorLiterals`** — float[] → pgvector literal, used by both write and read paths.

**repository/`ConversationTurnRepository`** — Mongo repository with one derived query.

**entity/`DocumentEntity` / `DocumentChunkEntity`** — JPA mappings of the two Postgres tables; the embedding column is intentionally unmapped (the comment explains). **`DocumentStatus`** — the four-state lifecycle. **`ConversationTurn`** — the Mongo document, embedding `Citation` so the stored shape equals the API shape.

**dto/** — request/response records, the Kafka event record, `Citation`, and the uniform `ErrorResponse`.

**exception/`GlobalExceptionHandler`** — every failure becomes the same JSON shape with the right status (400 validation, 404 not-found, 413 too-large, 429 rate-limited, 500 generic-without-leaking).

**Tests** — `TextChunkerTest` proves sizes, overlap, and lossless reassembly arithmetically; `DocumentServiceTest` proves validation (including the spoofed-extension case) and the store→save→publish sequence; `AskServiceTest` proves the vector literal and top-K reach the repository, documentId routes to the scoped query, and — most importantly — that with no retrieved chunks the chat model is *never called*.

---

## 3. Deep dives

### Chunking

Why ~800 tokens? It's the balance point of three pressures. **Retrieval precision:** the embedding of a chunk is one vector summarizing all of it; the bigger the chunk, the more topics that single vector has to average over, and the blurrier it becomes. **Context quality:** the chunk must carry enough surrounding text that the LLM can actually use it — a lone sentence often can't support an answer. **Budget:** four chunks of 800 tokens ≈ 3,200 tokens of context per question, comfortable for any modern model and cheap.

Much **bigger** chunks (say 4,000 tokens): each vector becomes a muddy average of many topics, so similarity search starts returning chunks that are "sort of about everything" — recall degrades precisely when documents are diverse, and your token bill quadruples. Much **smaller** chunks (say 100 tokens): each vector is razor-sharp, but the retrieved snippets lack context ("it must be returned within that period" — what must?), and answers fragment across chunks the retriever can't stitch back together.

The 100-token **overlap** ensures no sentence is ever lost to a boundary: any passage cut by the end of chunk N appears whole at the start of chunk N+1. The cost is ~14% storage and embedding duplication — cheap insurance.

The characters/4 heuristic: exact token counts require the model's tokenizer, but chunking only needs to be approximately right, and English averages ~4 characters per token. Worst case we're 20% off, which still leaves chunks far below the embedding model's 8,191-token limit.

### Embeddings

`text-embedding-3-small` maps text to 1,536 floating-point numbers. No individual dimension "means" anything human-readable; what matters is the *geometry* — the model was trained so that semantically similar texts produce nearby vectors. The intuition: a library where shelf position is decided by what a book is *about*, learned from reading billions of sentences. "Refund policy" and "money-back guarantee" sit on the same shelf despite sharing zero words. 1,536 dimensions sounds extravagant, but meaning has many independent axes — topic, tone, specificity, domain, time, polarity… — and high dimensionality is what lets all of them coexist without crowding.

### Cosine similarity

Two vectors are similar if they *point in the same direction*; their lengths are irrelevant for meaning. Cosine similarity is the cosine of the angle between them:

```
cos(θ) = (A · B) / (||A|| × ||B||)
```

— the dot product normalized by both magnitudes. It ranges from 1 (same direction) through 0 (orthogonal, unrelated) to −1 (opposite). pgvector's `<=>` operator returns cosine *distance* = 1 − similarity, so **smaller is better** and `ORDER BY embedding <=> :question LIMIT 4` reads exactly as "the 4 chunks pointing most nearly the same way as the question."

### pgvector indexing: IVFFlat vs HNSW

Without an index, every query compares the question vector against every chunk — exact but O(n). **IVFFlat** clusters vectors into `lists` buckets via k-means at index-build time; a query finds the few nearest cluster centroids and scans only those buckets. Cheap to build, light on memory, slightly approximate (a true neighbour just across an unprobed cluster boundary gets missed). Its quirk: clusters reflect the data present when the index was built, so after a big load you `REINDEX`. **HNSW** instead maintains a multi-layer navigable graph — better recall/latency at scale, no training step (handles rows inserted after creation gracefully), but slower builds and a much larger memory footprint. The decision rule I use: IVFFlat until you have millions of vectors or measurable recall problems; the switch is a one-line index swap and zero application changes — which is itself an argument for pgvector.

### Grounded prompting and hallucination control

Four layers, cheapest first: (1) **retrieval scoping** — the model only ever sees text that actually came from the user's documents; (2) **instruction** — the system prompt says answer ONLY from the context, cite every claim as `[filename, chunk N]`, and reply with an exact sentinel when the context is insufficient — the sentinel matters because exact-string outputs are testable and detectable; (3) **temperature 0.2** — low randomness keeps the model on the rails; (4) **the short-circuit in code** — zero retrieved chunks means we return the sentinel ourselves and never give the model a chance to improvise. Citations double as a *human* verification loop: every claim is one click from its source. None of this is perfect — the model can still misread context — but it turns "trust the model" into "verify the model."

### Why Kafka sits in the ingestion path

Ingesting a PDF takes seconds to minutes (extraction + many embedding API calls). Doing that inside the HTTP request would mean timeouts, double-submits, and a thread pool drained by slow work. With Kafka: the upload returns in milliseconds with 202 (**async**); a burst of 50 uploads queues calmly instead of stampeding the OpenAI rate limit (**backpressure** — consumers pull at their own pace); transient failures replay automatically with exponential backoff (**retries**); poisoned inputs end up parked on `document-events.DLT` with the document marked FAILED, inspectable and replayable instead of lost (**DLT**); and because at-least-once delivery means duplicates *will* happen, the consumer is **idempotent** — the READY guard plus delete-before-reinsert make redelivery harmless. Partitioning by documentId also gives free ordering per document and free horizontal scaling: add consumers up to the partition count.

### Hosted APIs vs local models

Hosted (what we chose): zero infrastructure, frontier quality, pay-per-token (~$0.0007 per question — see the cost math in Q&A), but you accept per-minute rate limits, network latency (0.5–2s per chat call), and the fact that document text leaves your boundary (OpenAI's API terms exclude training on API data, but for healthcare/legal/defence that may be insufficient). Local (Ollama, vLLM with Llama/Mistral, plus a local embedding model): data never leaves, no rate limits, flat hardware cost — at the price of GPU ops, lower quality at comparable sizes, and you become your own SRE. The pivot points to a local model: hard data-residency requirements, or sustained volume where token spend exceeds the cost of a GPU box. Spring AI keeps that door open — an Ollama starter swaps in the same way the Claude swap works.

### What changes at 10x scale

At roughly 10x current load: (1) run multiple app instances behind a load balancer — everything stateful already lives in Postgres/Mongo/Kafka, except the rate limiter, which moves to Redis; (2) raise partitions on `document-events` and scale ingestion consumers to match; (3) connection-pool tuning and read replicas on Postgres; consider HNSW as chunk count climbs; (4) batch and parallelize embedding calls harder, watching the provider's rate-limit tiers; (5) add a re-ranking step (retrieve 20, re-rank to 4) for quality at scale; (6) cache embeddings of repeated questions. The architecture itself doesn't change — that's the point of starting with queues and stateless services.

---

## 4. Interview Q&A (first person, as the builder)

**Q: Why ~800-token chunks with 100 overlap? What happens with much bigger or smaller chunks?**

I picked ~800 tokens as the equilibrium between retrieval precision and answer context. One embedding summarizes a whole chunk, so chunk size sets the resolution of search: go much bigger — say 4,000 tokens — and each vector averages several topics together, retrieval gets blurry, and I'm paying to stuff mostly-irrelevant text into every prompt. Go much smaller — say 100 tokens — and the vectors are precise but the retrieved fragments lose their surrounding context, so the model gets sentences it can't interpret, and answers that span chunks fall apart. The 100-token overlap means a sentence cut at a chunk boundary always appears intact in the neighbouring chunk; it costs about 14% duplication, which is cheap insurance. I estimate tokens as characters divided by four because chunking only needs to be roughly right — exact counting would need the model's own tokenizer for marginal benefit.

**Q: How does the similarity search work — what is cosine similarity doing?**

Every chunk was embedded into a 1,536-dimensional vector at ingestion, and I embed the incoming question with the same model, so question and chunks live in one semantic space. Cosine similarity measures the angle between two vectors — dot product over the product of magnitudes — so it's asking "do these point the same way?", which after training corresponds to "do these mean similar things?". pgvector's `<=>` operator gives cosine distance, one minus similarity, so my query is literally `ORDER BY embedding <=> :questionVector LIMIT 4`: the four chunks whose meaning points most nearly in the question's direction, found via the IVFFlat index instead of a full scan.

**Q: Why pgvector instead of Pinecone or a dedicated vector DB?**

Operational simplicity at my actual scale. I already need Postgres for document metadata, so pgvector means one fewer system to deploy, secure, back up, and pay for — and my vectors live transactionally next to the rows they belong to: when a document is deleted, `ON DELETE CASCADE` cleans its chunks, no cross-system consistency dance. Dedicated vector DBs earn their keep at hundreds of millions of vectors with hard latency SLOs and managed-service requirements. At tens of thousands of chunks, pgvector with an IVFFlat index answers in milliseconds. And because the repository method is plain SQL behind an interface, migrating later is a bounded change, not a rewrite.

**Q: Walk me through what happens when a document is uploaded. Why is Kafka in that path?**

The controller validates the file — size, type, and the `%PDF` magic bytes, since extensions are client-supplied — then stores it on disk under a server-generated UUID, inserts a metadata row with status UPLOADED, publishes a JSON event to the `document-events` topic keyed by documentId, and returns 202 in milliseconds. A consumer in the `documind-ingestion` group picks the event up, flips status to PROCESSING, extracts text with PDFBox, chunks it, calls the embeddings API in batches of 20, inserts chunks and vectors into pgvector, and marks the document READY. Kafka is there because ingestion takes seconds to minutes and doesn't belong inside an HTTP request: I get an instant, durable acknowledgment for the client; backpressure when fifty documents arrive at once, instead of stampeding the embeddings rate limit; automatic retries with exponential backoff for transient failures; and a dead-letter topic where genuinely poisoned events get parked — with the document marked FAILED so the user sees the truth — rather than lost or retried forever. Since Kafka is at-least-once, the consumer is idempotent: an already-READY document is skipped, and chunks are deleted before re-insert so redelivery can't duplicate data.

**Q: How do you stop the LLM from making things up?**

Four layers. First, retrieval scoping: the model only ever sees text retrieved from the user's own documents. Second, the prompt contract: answer only from the provided context, cite every claim as [filename, chunk N], and if the context doesn't contain the answer, reply with an exact sentinel sentence — exact, because exact strings are testable and detectable downstream. Third, temperature 0.2, so the model doesn't get creative. Fourth — and this is the one most people skip — a guard in code: if retrieval returns zero chunks, my service returns the sentinel itself and never calls the model, because no context means there's nothing legitimate to answer from. I have a unit test asserting the chat model is never invoked in that case. The citations also create a human verification loop: every claim is one click from its source.

**Q: Why OpenAI APIs, and what would switching the chat model to Claude involve?**

RAG needs two models — embeddings and chat — and Anthropic doesn't offer an embeddings API, so one OpenAI key covers both: one account, one billing relationship, and gpt-4o-mini plus text-embedding-3-small are both strong and extremely cheap. But I built it provider-pluggable through Spring AI: my services depend on the `ChatModel` and `EmbeddingModel` interfaces, never on OpenAI classes. Switching chat to Claude is adding the `spring-ai-starter-model-anthropic` dependency and flipping `spring.ai.model.chat` to `anthropic` in YAML, with embeddings pinned to OpenAI — zero Java changes. Embeddings, though, are sticky in a different way: every stored vector came from a specific embedding model, so changing the *embedding* model means re-embedding the whole corpus — which is also my answer for why I'd choose an embedding model more carefully than a chat model.

**Q: What breaks with a 500-page document, and how would you fix it?**

Around 250k tokens of text, three things strain. Memory: PDFBox materializes the document and I hold the full extracted string plus all chunks — fixable by extracting and embedding page-ranges streaming-style instead of whole-document. Time: ~300+ chunks means 16+ embedding batches; a transient failure late in the run currently restarts ingestion from zero — fixable with chunk-level checkpointing, recording the last completed batch and resuming. And the Kafka consumer could exceed `max.poll.interval.ms` while it grinds, triggering a rebalance and redelivery — fixable by moving the heavy work off the listener thread or splitting the pipeline into extract → chunk → embed stages with intermediate topics. My idempotency guard means none of this corrupts data today; it just wastes work. Retrieval quality also dilutes across 300 chunks, so I'd add a re-rank step — retrieve 20, re-rank to 4.

**Q: How would you scale this to 1,000 concurrent users?**

The asks dominate, so: run N stateless app instances behind a load balancer — all state already lives in Postgres, Mongo, and Kafka; the one in-memory piece, the rate limiter, moves to Redis. Postgres gets a connection pooler and read replicas for the similarity queries; at millions of chunks I'd flip IVFFlat to HNSW for better recall/latency. The real bottleneck becomes the LLM provider's rate limits, so I'd add request queuing with caps, cache embeddings for repeated questions, and consider caching full answers for hot question+document pairs. Ingestion scales independently — that's the payoff of the Kafka split: more partitions, more consumers, done. Then observability: token spend, retrieval latency, and answer latency per request, because at that scale cost is a feature you engineer.

**Q: What does each question cost? Show the math.**

Embedding the question: ~30 tokens at $0.02 per million = $0.0000006 — effectively free. The chat call: input is the system prompt (~80 tokens) + 4 chunks × 800 tokens + the question ≈ 3,300 tokens at $0.15 per million = ~$0.0005; output ~250 tokens at $0.60 per million = ~$0.00015. Total ≈ **$0.00065 per question — about 1,500 questions per dollar.** Ingestion: a 100-page PDF is ~65k tokens; with 14% overlap, ~75k embedded tokens at $0.02 per million ≈ **$0.0015 — a seventh of a cent per document.** That arithmetic is also why I'm relaxed about overlap duplication and why caching matters only at much higher volume.

---

## 5. Glossary

- **RAG** — answering with an LLM whose prompt is augmented by retrieved documents, so answers are grounded rather than recalled.
- **Embedding** — a learned mapping from text to a vector such that similar meanings produce nearby vectors.
- **Vector / dimension** — a list of floats; each of the 1,536 positions is one dimension of the space.
- **Token** — the sub-word unit models read and bill by; ~4 English characters on average.
- **Chunking** — splitting documents into retrieval-sized pieces, each embedded separately.
- **Overlap** — repeating the tail of one chunk at the head of the next so boundaries never lose a sentence.
- **Cosine similarity** — closeness of two vectors' directions; the angle's cosine.
- **Cosine distance** — 1 − cosine similarity; pgvector's `<=>`; smaller = more similar.
- **Vector store** — a database that indexes vectors for nearest-neighbour search (here: pgvector).
- **ANN (approximate nearest neighbour)** — trading a little recall for sub-linear search time.
- **IVFFlat** — ANN index that k-means-clusters vectors and probes only the nearest clusters.
- **HNSW** — ANN index built as a layered proximity graph; better at scale, hungrier to build.
- **Recall (retrieval)** — fraction of the truly relevant items the search actually returned.
- **Top-K** — how many nearest chunks are retrieved (4 here).
- **Grounding** — constraining the model to answer only from supplied context.
- **Hallucination** — fluent, confident, unsupported model output.
- **System prompt** — the instruction message that sets the model's rules before user content.
- **Temperature** — sampling randomness; low = deterministic and literal.
- **Context window** — maximum tokens a model can attend to per request.
- **Citation** — the [filename, chunk N] pointer tying a claim to its source chunk.
- **Consumer group** — Kafka consumers sharing one subscription; partitions divide among them.
- **At-least-once** — delivery guarantee implying duplicates; the reason idempotency exists.
- **Idempotency** — processing the same message twice has the same effect as once.
- **Backpressure** — consumers pulling at their own pace instead of being flooded.
- **DLT (dead-letter topic)** — where messages go after exhausting retries, for inspection/replay.
- **Exponential backoff** — retry delays that double, giving transient failures room to clear.

---

## 6. "Explain it like I built it" — the 60-second version

> "DocuMind is a document Q&A service I built to learn GenAI engineering properly — production patterns, not a notebook demo. You upload PDFs and ask questions in natural language; answers come back grounded in your documents, with citations. Upload-side, the API stores the file and returns 202 immediately, and a Kafka consumer does the heavy lifting asynchronously: PDFBox extracts the text, I chunk it into roughly 800-token windows with overlap, embed the chunks with OpenAI's embedding model in batches, and store the vectors in Postgres with pgvector — with retries, a dead-letter topic, and an idempotent consumer, because at-least-once delivery means duplicates are a when, not an if. Ask-side, I embed the question, run a cosine-similarity search for the four nearest chunks, and send the model a grounded prompt: answer only from this context, cite every claim, and say 'I don't have enough information' if it's not there — and if retrieval comes back empty, my code returns that sentence itself without calling the model. Conversation history lands in MongoDB. It's all Spring AI interfaces, so swapping the chat model to Claude is a YAML change I've already wired up. Each question costs about a fifteenth of a cent, and I can walk you through where every one of those numbers comes from."

---

*Now close this file and have Claude quiz you on Section 4 — one question at a time, no peeking.*
