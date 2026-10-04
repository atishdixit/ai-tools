# Proposal: A company-profile chatbot that answers from your own documents

**Status:** proposal for the demo in this folder. The demo is implemented; see the [README](../README.md) for how to run it and the
measured results.

## 1. What we want

A chatbot that knows **one company's own information** (who they are, products, prices, policies, contacts) and answers
questions **using only that information**. When it does not know, it says so instead of inventing an answer. When the company's
documents change, the bot's answers change, without anyone retraining a model.

## 2. What "training the chatbot" really means

People say "train the bot on our data". For this use case that is **not** model training. Training or fine-tuning a language model:

- is slow and costly, and needs a lot of well-prepared examples;
- teaches *style and behaviour*, not reliable facts: a fine-tuned model still mixes up or invents details;
- goes stale the moment a price or policy changes, and the only fix is to train again.

The proven approach is **RAG, retrieval-augmented generation**:

```
documents ──► split into passages ──► turn each into numbers (embeddings) ──► search index      (done once, and again when files change)

question ──► find the most relevant passages ──► give them to the language model ──► answer with sources     (done for every question)
```

So "training" the bot means **putting the company's files in a folder and indexing them**. That takes seconds, costs nothing, and
can be repeated whenever the documents change. The language model is only used to *phrase an answer from the passages it is handed*.

## 3. Build it or use an existing product?

| Option | Examples (verify current features) | Good for | Not so good for |
|--------|------------------------------------|----------|-----------------|
| **A. No-code open-source apps** | Open WebUI, AnythingLLM, PrivateGPT, Flowise, Dify | Getting an internal "chat with our documents" tool running in an hour; running locally with Ollama | Embedding in your own product, custom access rules, systematic testing, deep control of retrieval |
| **B. Hosted chatbot services** | Many commercial website-chatbot builders | Marketing sites with no engineering effort | Cost per message/seat, data leaves your control, limited testing and customisation |
| **C. Build it (this demo)** | Own code, local model through Ollama | Understanding and controlling every step; measuring quality; embedding anywhere; privacy | You maintain it |

**Recommendation.** For a **demo that shows how it works and proves it with tests**, build it from scratch (option C): it is small
(a few thousand lines), has no framework magic, and lets us measure quality honestly. For a **quick internal tool**, option A is
usually the better choice, and this demo makes it clear what those tools do inside. A hosted service (B) only makes sense when
nobody can maintain software.

## 4. The demo

| Aspect | Decision |
|--------|----------|
| **Company** | A fictional company, **Zenith Cloudworks**, so nothing real is involved |
| **Knowledge** | 9 documents in `company-data/`: about, products and pricing, support policy, HR policy, security, team and contacts, FAQ, announcements (plain text) and a PDF handbook |
| **Language model** | Runs **locally** with Ollama (`llama3.2:3b`), free, no account and no data leaving the PC |
| **Embedding model** | `nomic-embed-text`, also local |
| **Search** | Hybrid: meaning-based (vectors) plus keyword-based (BM25), combined |
| **Behaviour** | Answers only from documents, cites the sources, says "I don't have that information" otherwise |
| **Interface** | A small web chat page with streaming answers and a sources panel; also a REST API |
| **Run** | `setup.bat` once, `run.bat` to start |

### What the user sees

1. Opens `http://localhost:8090` and asks "What is the price of the Pro plan?"
2. The answer appears word by word, with the sources it used (file and section) listed underneath.
3. Asks something the documents do not cover ("What's the CEO's salary?") and gets a clear "I don't have that information".
4. Edits a document in `company-data/`, clicks **Re-index** (or calls `/api/ingest`), asks again and gets the new answer.

## 5. Testing plan: "does it actually work?"

A chatbot must be **measured**, not judged by a few good-looking answers.

| Test | What it proves |
|------|----------------|
| **Unit tests** (chunking, keyword search, vector search, rank fusion, loaders, prompt building, history) | The building blocks are correct |
| **Pipeline tests with a fake model server** | Ingestion, incremental re-indexing, graceful handling of a model that is down or slow, streaming |
| **API tests** | Validation (empty/oversized questions), errors, sources in every answer |
| **Evaluation with the real local model**: 46 questions across 10 categories, each with the facts the answer must contain | Real answer quality |

Evaluation categories: direct facts, numbers and prices, wording that differs from the documents (paraphrase), answers spread over
several passages, follow-up questions, faithfulness (including a negative fact and an in-progress certification), **questions the
documents cannot answer** (must be refused), **confidential-data requests** (must be refused), **prompt-injection attempts**, and
**documents that contain planted instructions**. Each question is scored automatically and the report lists every failure.
The questions are split into a *development* set (used while tuning) and a *hold-out* set (never used for tuning), so the numbers
we report are not flattering ourselves.

**Targets for the demo** (stated before measuring): retrieval finds the right source for at least 90% of answerable questions;
at least 80% of answers contain the required facts; **100% of unanswerable and confidential questions are refused**; no prompt
injection succeeds. The README states what was actually achieved, including misses.

## 6. Honest limits

| Limit | Consequence | Mitigation |
|-------|-------------|------------|
| A **3-billion-parameter model on a CPU-only laptop** | Slower (seconds per answer) and less careful than large hosted models; may miss details or phrase awkwardly | Keep prompts short and passages few; stream answers; swap in a bigger local or hosted model by changing one setting |
| Any LLM can still **hallucinate** | A wrong detail is possible even with correct passages | Strict instructions, a relevance gate that refuses before calling the model, always showing sources so a human can check |
| **Retrieval can miss** the right passage | The bot says "I don't know" for something it should know | Hybrid search, heading-aware chunks, evaluation to find misses |
| **Conflicting or outdated documents** | The bot may quote an old figure | Keep one source of truth; remove superseded files and re-index; show source names |
| **Access control** | In this demo everyone sees everything | Production design below: permissions per document, enforced at retrieval time |
| **Not a chat for decisions** | HR, legal or financial advice needs a human | Escalation message and contact details in the documents |

## 7. From demo to production

| Concern | What changes |
|---------|--------------|
| Model quality | A larger local model or a hosted model; compare them on the same evaluation set before switching |
| Scale | A vector database (pgvector, Qdrant or similar) instead of the in-memory index; background ingestion |
| Documents | More formats (Word, spreadsheets, web pages, wikis, ticket systems); automatic sync; deleting and versioning |
| Access | Login, per-user or per-team document permissions enforced when retrieving |
| Channels | Website widget, Telegram, WhatsApp, Slack, Teams |
| Quality | Evaluation on every change; thumbs up/down feedback; review of questions the bot refused |
| Operations | Tracing, cost and latency monitoring, rate limits, backups, retention and privacy rules |
| Multiple companies | One index per company (tenant), isolated storage and configuration |

## 8. Effort

| Stage | Estimate |
|-------|----------|
| This demo (built here) | about 1 to 2 days |
| Pilot for one company: real documents, a website widget, login, evaluation set from real questions | 2 to 4 weeks |
| Production, multi-channel, access control and monitoring | 6 to 10 weeks |

Estimates are indicative and depend mostly on how clean and well-owned the company's documents are.

## 9. Questions to settle before a real project

1. Which documents are the single source of truth, and who keeps them current?
2. Who may ask what (employees, customers, the public)? Are some documents confidential?
3. Which channels (website, WhatsApp, Telegram, Teams)?
4. May company data be sent to a hosted model provider, or must everything stay on company machines?
5. What should the bot do when it does not know: hand over to a person, collect an email, open a ticket?
6. Which languages?
