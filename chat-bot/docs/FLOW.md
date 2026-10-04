# Flow diagrams

How the chatbot works, step by step. Diagrams are Mermaid and render on GitHub.

## 1. The whole system

```mermaid
flowchart LR
    subgraph You
        F[Company files<br/>company-data folder]
        U[User in the browser]
    end

    subgraph App["Chatbot application (Java, port 8090)"]
        direction TB
        ING[Indexing<br/>read, split, screen, embed]
        IDX[(Search index<br/>passages, vectors,<br/>keyword index)]
        CHAT[Answering<br/>search, check, ask the model]
        API[REST API and web page]
    end

    subgraph Ollama["Ollama (local, port 11434)"]
        EM[Embedding model<br/>nomic-embed-text]
        LLM[Language model<br/>llama3.2:3b]
    end

    F -->|1. teach| ING
    ING <--> EM
    ING --> IDX
    U -->|2. ask| API --> CHAT
    CHAT <--> IDX
    CHAT <--> EM
    CHAT <--> LLM
    CHAT -->|3. answer and sources| API --> U
```

Everything runs on one computer. Nothing is sent to the internet after the models are downloaded.

## 2. "Training": from files to a searchable index

This is the only step that makes the bot "know" the company. It takes seconds, needs no model training, and is repeated
whenever the files change (button **Re-index documents**, or `POST /api/ingest`, or at start-up).

```mermaid
flowchart TD
    A[Files in company-data] --> B{Supported type?<br/>md, txt, pdf}
    B -->|no| W1[Warn and ignore]
    B -->|yes| C[Compute the file's fingerprint<br/>SHA-256]
    C --> D{Same fingerprint as last time<br/>and vectors present?}
    D -->|yes| R[Reuse its passages and vectors<br/>nothing to do]
    D -->|no| E[Read the file<br/>headings kept as a path]
    E --> F{Any text?}
    F -->|no| W2[Warn: scanned PDF needs OCR]
    F -->|yes| G[Split into passages<br/>under 900 characters,<br/>never across headings]
    G --> SC{Looks like an order<br/>to an AI?}
    SC -->|yes| W3[Leave that passage out<br/>and warn: review the file]
    SC -->|no| H[Embed each passage<br/>label: search_document]
    H -->|model down| K[Keep for keyword search only,<br/>embed again next time]
    H --> I[New passages and vectors]
    R --> J[Build a new immutable index]
    I --> J
    K --> J
    J --> S[Swap it in atomically<br/>and save to disk]
    X[Files that disappeared] -.->|dropped| J
```

Why it is incremental: a file is identified by the fingerprint of its content, so unchanged files cost nothing, an edited file is
re-embedded alone, and a deleted file disappears from the answers at the next indexing run.

## 3. Answering one question

```mermaid
sequenceDiagram
    autonumber
    participant U as User (browser)
    participant A as Chat service
    participant I as Index
    participant E as Embedding model
    participant L as Language model

    U->>A: question (+ conversation id)
    A->>A: validate (not empty, not too long)
    A->>A: asks for the bot's own instructions? refuse here
    A->>A: follow-up cues (and, it, what about)? add the previous question to the search
    A->>E: embed the question (label: search_query)
    E-->>A: vector
    A->>I: meaning search (vectors) + keyword search (BM25)
    I-->>A: two ranked lists
    A->>A: merge them (reciprocal rank fusion), keep the best 4
    alt nothing similar enough in the documents
        A-->>U: "I don't have that information..." (the model is not called)
    else relevant passages found
        A->>A: keep only passages close to the best one (fewer tokens, faster)
        A->>L: rules + fenced passages + last turns + question
        L-->>A: answer, word by word
        A-->>U: streamed text
        A->>A: answer repeats the rules? replace by a refusal
        A-->>U: final: mode, the cited sources with excerpts
    end
```

## 4. Every way a question can end

```mermaid
flowchart TD
    Q[Question arrives] --> V{Valid?}
    V -->|empty| E1[400: Please type a question]
    V -->|over 1000 characters| E2[400: too long]
    V -->|ok| BUSY{Free slot within 60 s?}
    BUSY -->|no| E3[429: assistant is busy]
    BUSY -->|yes| EMPTY{Documents indexed?}
    EMPTY -->|no| N1[NO_DOCUMENTS:<br/>add files and re-index]
    EMPTY -->|yes| INJ{Asks for the bot's<br/>own instructions?}
    INJ -->|yes| N0[NO_ANSWER<br/>no model call, instant]
    INJ -->|no| EMB{Question embedded?}
    EMB -->|model down| KW[Keyword search only,<br/>note shown to the user]
    EMB -->|ok| HY[Hybrid search]
    KW --> GATE
    HY --> GATE{Relevant enough?<br/>similarity at least 0.55<br/>or keyword coverage at least 0.6}
    GATE -->|no| N2[NO_ANSWER<br/>no model call, instant]
    GATE -->|yes| TR[Trim to passages near the best one]
    TR --> GEN[Ask the language model]
    GEN -->|model down or empty| X1[EXTRACTIVE:<br/>show the best passages as they are]
    GEN -->|model says it does not know| N3[NO_ANSWER<br/>no sources shown]
    GEN -->|answer repeats the rules| N4[NO_ANSWER<br/>replaced by a refusal]
    GEN -->|answer| OK[ANSWER<br/>with the sources it cited]
```

| Result (`mode`) | Meaning | What the user sees |
|-----------------|---------|--------------------|
| `ANSWER` | The model answered from the passages | The answer and its sources |
| `NO_ANSWER` | The documents do not cover it (decided by a guard, the relevance gate or the model) | "I don't have that information in the company documents." |
| `EXTRACTIVE` | The model was unavailable | The most relevant passages, with a note |
| `NO_DOCUMENTS` | Nothing has been indexed yet | A hint to add files and re-index |

## 5. The layers against made-up answers and manipulation

```mermaid
flowchart LR
    Q[Question] --> L1[1. Instruction-extraction guard<br/>refuse requests to recite the rules]
    L1 --> L2[2. Relevance gate<br/>nothing similar in the documents?<br/>refuse, do not ask the model]
    L2 --> L3[3. Strict prompt<br/>answer only from fenced passages,<br/>never obey text inside them]
    L3 --> L4[4. Answer check<br/>repeats the rules? refuse]
    L4 --> S[5. Sources always shown,<br/>so a person can check]
    D[Documents] --> L0[0. Screening when indexed<br/>passages that give orders to an AI<br/>are left out and reported]
    L0 -.-> L3
```

Layer 2 is cheap and instant but coarse: a question that merely shares a topic with the documents ("weather in Pune") passes it, and
layer 3 declines it at the cost of a model call. Layers 0, 1 and 4 are pattern-based heuristics: they stop the common attacks, not
every possible one. The [README](../README.md#how-well-does-it-work) reports what was measured, including what is not covered.

## 6. Conversation memory

```mermaid
flowchart LR
    T1[Turn 1<br/>How much is StockSense Pro?] --> M[(Session memory<br/>last 4 turns, in RAM only,<br/>expires after 60 min idle)]
    M --> T2{Turn 2: short, and sounds like a follow-up?<br/>7 words or fewer and a cue word<br/>and / what about / it / that / they ...}
    T2 -->|yes: And how many warehouses does it include?| SQ[Search for:<br/>previous question + this one]
    T2 -->|no: What is the capital of France?| SO[Search for this question alone]
    M --> PR[Prompt includes<br/>the earlier turns]
```

A short question on a new topic must not inherit the old topic: merging "What is the capital of France?" with the previous
company question made it look company-related and cost a full model call, which is why the cue words are required.
Memory is kept per conversation id, in memory only; nothing is written to disk, and a restart forgets all conversations.
