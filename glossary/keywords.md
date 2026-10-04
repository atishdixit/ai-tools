# Keywords

Plain-language definitions of the terms you will meet, grouped by branch. Each is one line: enough to recognise the word and know
where to dig deeper.

[← Back to the roadmap](../README.md)

**Contents:** [Core ideas](#core-ideas) · [Machine learning](#machine-learning) · [Data science and statistics](#data-science-and-statistics) ·
[Deep learning](#deep-learning) · [Language models](#language-models-and-prompting) · [Retrieval and chatbots](#retrieval-chatbots-and-assistants) ·
[Agents](#agentic-ai) · [Generative media](#generative-media-and-video) · [Algorithms](#algorithms) · [Production](#mlops-and-production) ·
[Responsible AI](#responsible-ai-and-security) · [Research](#research)

## Core ideas

| Term | Meaning |
|------|---------|
| **AI (artificial intelligence)** | Software doing tasks that normally need human intelligence: perceiving, deciding, understanding language |
| **Machine learning (ML)** | Learning patterns from data instead of writing rules by hand |
| **Deep learning (DL)** | ML with many-layered neural networks |
| **Generative AI** | Models that create new content: text, images, audio, video, code |
| **Foundation model** | A large model trained on broad data that can be adapted to many tasks |
| **Narrow vs general AI** | Narrow AI does one kind of task; "general" AI (AGI) would match human ability across tasks, and does not exist today |
| **Model** | The trained function (weights plus architecture) that turns inputs into outputs |
| **Training vs inference** | Training builds the model from data; inference uses it to answer |
| **Parameter / weight** | A number the model learns; "7B" means 7 billion of them |
| **AI-enabled** | An existing product with AI features added |
| **AI-native** | A product built around AI so that it cannot exist without it |
| **Human-in-the-loop** | A person reviews or approves the AI's output or action |

## Machine learning

| Term | Meaning |
|------|---------|
| **Supervised learning** | Learning from labelled examples |
| **Unsupervised learning** | Finding structure in unlabelled data |
| **Self-supervised learning** | Creating labels from the data itself, e.g. predict the next word |
| **Reinforcement learning (RL)** | Learning by trial and reward |
| **Classification / regression** | Predict a category / predict a number |
| **Clustering** | Grouping similar items without labels |
| **Feature** | An input variable the model uses |
| **Label / target** | The correct answer the model should predict |
| **Training / validation / test set** | Data for learning / for tuning / for the final honest check |
| **Overfitting** | Memorising training data and failing on new data |
| **Underfitting** | Too simple to capture the pattern |
| **Bias-variance trade-off** | Simple models err by being wrong in a steady way; complex ones by being unstable |
| **Regularisation** | Techniques (L1, L2, dropout) that discourage overfitting |
| **Cross-validation** | Rotating which part of the data is held out to estimate performance reliably |
| **Data leakage** | Test or future information sneaking into training, inflating results |
| **Hyper-parameter** | A setting you choose before training (learning rate, tree depth) |
| **Ensemble** | Combining many models; e.g. random forests, gradient boosting |
| **Gradient boosting** | Building trees one at a time, each fixing the previous errors; strong on tabular data |
| **Precision / recall / F1** | Of the items flagged, how many were right / of the real ones, how many found / their balance |
| **ROC-AUC, PR-AUC** | Threshold-free summaries of classifier quality |
| **Confusion matrix** | Table of true/false positives and negatives |
| **Calibration** | Whether predicted probabilities match real frequencies |
| **Baseline** | A simple reference model you must beat |

## Data science and statistics

| Term | Meaning |
|------|---------|
| **EDA (exploratory data analysis)** | Looking at data to understand it before modelling |
| **ETL / ELT** | Extract, transform, load: moving and shaping data |
| **Data warehouse / lake** | Central store for analytics (structured) / for raw data of any kind |
| **SQL** | The standard language for querying relational data |
| **Outlier** | A value far from the rest; may be an error or the interesting part |
| **Correlation vs causation** | Two things moving together does not mean one causes the other |
| **Confounder** | A hidden factor that influences both cause and effect |
| **Hypothesis test / p-value** | A test of whether data is surprising under "no effect"; the p-value is not the chance the hypothesis is true |
| **Confidence interval** | A range that, over repeated studies, would contain the true value a stated share of the time |
| **A/B test** | Randomised comparison of two versions |
| **Statistical power** | The chance a test detects a real effect of a given size |
| **Time series** | Data ordered in time; needs time-based splits |
| **Data drift** | The data in production changes from what the model was trained on |

## Deep learning

| Term | Meaning |
|------|---------|
| **Neural network** | Layers of simple units whose connection weights are learned |
| **Neuron / layer** | One weighted sum plus activation / a group of neurons |
| **Activation function** | The non-linearity (ReLU, sigmoid, softmax) that lets networks learn complex patterns |
| **Loss function** | A number measuring how wrong the model is; training minimises it |
| **Gradient / gradient descent** | The slope of the loss / stepping downhill along it |
| **Backpropagation** | Computing gradients layer by layer with the chain rule |
| **Epoch / batch** | One pass over the data / a chunk of data per update |
| **Learning rate** | The size of each update step |
| **Optimiser (SGD, Adam)** | The rule for applying gradients |
| **Dropout / batch norm / layer norm** | Techniques that stabilise and regularise training |
| **CNN (convolutional neural network)** | Network for images using sliding filters |
| **RNN / LSTM** | Networks for sequences with a memory; largely replaced by transformers |
| **Attention** | Letting each element weigh how much to focus on every other element |
| **Transformer** | The architecture built on self-attention; the basis of modern LLMs |
| **Embedding** | A list of numbers representing meaning; similar things are close |
| **Latent space** | The compressed internal representation a model works in |
| **Transfer learning** | Reusing a pre-trained model for a new task |
| **Fine-tuning** | Further training a pre-trained model on your data |
| **LoRA / QLoRA** | Fine-tuning by training small added matrices, cheaply |
| **Quantisation** | Storing weights with fewer bits to shrink and speed up a model |
| **Distillation** | Training a small model to imitate a big one |
| **GPU / TPU** | Chips for the parallel maths of neural networks |
| **PyTorch / JAX / TensorFlow** | Deep learning frameworks |

## Language models and prompting

| Term | Meaning |
|------|---------|
| **LLM (large language model)** | A transformer trained on huge text to predict the next token |
| **Token** | A piece of text (word or part of a word) the model reads and writes; pricing is per token |
| **Context window** | How much text (tokens) the model can consider at once |
| **Prompt / system prompt** | The input instruction / the standing instructions that set behaviour |
| **Temperature / top-p** | Settings controlling randomness in the output |
| **Few-shot / zero-shot** | Giving examples in the prompt / giving none |
| **Chain of thought** | Having the model work through steps before answering |
| **Reasoning model** | A model trained to spend extra computation thinking before it answers |
| **Hallucination** | A fluent but false or invented answer |
| **Structured output / JSON mode** | Constraining output to a schema |
| **Function / tool calling** | The model asks your code to run a function with arguments |
| **Pre-training / instruction tuning** | Learning language from raw text / learning to follow instructions |
| **RLHF / DPO** | Tuning a model toward human-preferred answers |
| **Open-weight model** | A model whose weights you can download (licence terms vary) |
| **Mixture of experts (MoE)** | A model that activates only some of its parts for each token |
| **Small language model (SLM)** | A compact model that can run cheaply or on a device |
| **Multimodal** | Handles more than one kind of input (text, image, audio) |
| **Benchmark / contamination** | A standard test / when test data leaked into training so scores overstate ability |
| **LLM-as-judge** | Using a model to grade another model's output |

## Retrieval, chatbots and assistants

| Term | Meaning |
|------|---------|
| **Chatbot** | A program that converses to answer or complete a task |
| **AI assistant** | A chatbot with context, memory and the ability to act |
| **Intent / slot / entity** | What the user wants / a detail needed to do it / a recognised item in the text |
| **NLU / NLP** | Understanding / processing human language by machine |
| **Dialogue management** | Deciding what the bot says or does next |
| **RAG (retrieval-augmented generation)** | Fetching relevant documents and giving them to the model to answer from |
| **Vector database / index** | A store for embeddings supporting similarity search |
| **Chunking** | Splitting documents into pieces for retrieval |
| **Semantic search** | Searching by meaning using embeddings |
| **BM25 / hybrid search** | Keyword ranking / combining keyword and vector search |
| **Reranker** | A model that reorders retrieved results by relevance |
| **Grounding / citation** | Tying an answer to its sources |
| **Guardrails** | Checks that keep input and output inside rules |
| **Hand-off** | Passing the conversation to a human |
| **ASR / TTS** | Speech-to-text / text-to-speech |
| **Webhook** | A URL a service calls to deliver events to you (e.g. a new chat message) |

## Agentic AI

| Term | Meaning |
|------|---------|
| **Agent** | An LLM-driven system that plans, uses tools and acts over several steps toward a goal |
| **Agentic workflow** | Fixed steps involving LLM calls, as opposed to an open-ended agent |
| **Tool** | A function or service an agent can call (search, code, database, API) |
| **ReAct** | Alternating reasoning and acting with tools |
| **Planning / reflection** | Deciding steps in advance / critiquing and improving one's own output |
| **Orchestrator-worker** | A lead model delegating sub-tasks to others |
| **Multi-agent system** | Several agents with different roles cooperating |
| **MCP (Model Context Protocol)** | An open standard for connecting AI applications to tools and data |
| **A2A (Agent2Agent)** | A standard for agents to discover and delegate to each other |
| **Memory (short / long term)** | What an agent keeps for this task / across tasks |
| **Computer use** | An agent operating a screen, browser or app |
| **Sandbox** | An isolated environment where untrusted code can run safely |
| **Prompt injection** | Malicious instructions hidden in content the AI reads |
| **Trace** | The recorded sequence of an agent's steps, for debugging and audit |
| **Guardrail budget** | Limits on steps, time, tokens and cost |

## Generative media and video

| Term | Meaning |
|------|---------|
| **Diffusion model** | Generates by learning to remove noise step by step |
| **Text-to-video / image-to-video / video-to-video** | Clip from a prompt / from an image / restyled from a clip |
| **Temporal consistency** | Stable subjects and lighting across frames |
| **Seed** | The random start value; same seed, same setup reproduces a result |
| **ControlNet / conditioning** | Extra inputs (pose, depth, edges) that steer generation |
| **Prompt (cinematic)** | A description of subject, action, camera, lens, light and mood |
| **Upscaling / frame interpolation** | Increasing resolution / adding frames for smoothness |
| **Lip sync** | Matching mouth movement to speech |
| **Voice cloning** | Synthesising a specific person's voice; needs consent |
| **ComfyUI** | A node-based tool for building generation pipelines |
| **LoRA (media)** | A small add-on that teaches an image/video model a style or character |
| **Deepfake** | Synthetic media imitating a real person; harmful without consent |
| **C2PA / Content Credentials** | A standard for recording where media came from and how it was edited |

## Algorithms

| Term | Meaning |
|------|---------|
| **Big-O** | How cost grows with input size |
| **Heuristic** | A rule of thumb that guides search toward good answers |
| **A\*** | Shortest-path search guided by a heuristic |
| **Minimax / MCTS** | Game-tree search / search by random simulation |
| **Gradient descent** | Minimising a function by stepping against its slope |
| **Genetic algorithm** | Search by evolving a population of candidates |
| **k-means / DBSCAN** | Clustering methods |
| **PCA** | Reducing dimensions by keeping the directions of most variance |
| **Q-learning / PPO** | Reinforcement-learning methods |
| **Nearest-neighbour search (ANN, HNSW)** | Finding the closest vectors quickly |
| **Cosine similarity** | Closeness of two vectors by angle |
| **TF-IDF** | Weighting words by how distinctive they are in a document |
| **Tokenisation (BPE)** | Splitting text into model pieces by frequent patterns |
| **Beam search** | Keeping several best partial outputs while generating |
| **Dynamic programming** | Solving a problem by reusing answers to sub-problems |

## MLOps and production

| Term | Meaning |
|------|---------|
| **MLOps / LLMOps** | Practices for deploying and operating ML models / LLM applications |
| **Pipeline** | A repeatable sequence of data and training steps |
| **Experiment tracking** | Recording runs, settings and results |
| **Model registry** | A catalogue of model versions and their status |
| **Drift** | Data or behaviour changing after deployment |
| **Canary / shadow deployment** | Trying a change on a few users / on copies of traffic first |
| **CI/CD** | Automated build, test and release |
| **Feature store** | A shared, consistent store of model inputs |
| **Latency / throughput** | Time per request / requests per second |
| **Inference server (vLLM, Triton)** | Software that serves models efficiently |
| **Observability** | Metrics, logs and traces that explain system behaviour |
| **Regression test (evaluation set)** | Fixed examples rerun on every change to catch quality drops |
| **Idempotency** | Running an operation twice has the same effect as once |
| **Rate limit** | A cap on requests per time |

## Responsible AI and security

| Term | Meaning |
|------|---------|
| **Bias (in data / outcomes)** | Unfair skew in data or in results across groups |
| **Fairness metric** | A measurement of equal treatment (many definitions, trade-offs) |
| **PII (personally identifiable information)** | Data that identifies a person |
| **Consent / opt-out** | Permission given / the right to withdraw it |
| **Data minimisation / retention** | Collecting only what is needed / keeping it only as long as needed |
| **GDPR / DPDP / HIPAA** | EU privacy law / India's data protection law / US health-data law |
| **Jailbreak** | A prompt that bypasses a model's safety rules |
| **Red teaming** | Deliberately attacking your own system to find weaknesses |
| **Model card** | A document describing a model's purpose, data, performance and limits |
| **Explainability / interpretability** | Understanding why a model produced an output |
| **Alignment** | Making a system pursue what people actually intend |
| **Audit trail** | A tamper-resistant record of who did what |
| **EU AI Act** | EU regulation classifying AI systems by risk, applying in phases |
| **OWASP LLM Top 10** | A checklist of the main security risks for LLM applications |

## Research

| Term | Meaning |
|------|---------|
| **arXiv** | Free preprint server where most AI papers appear first |
| **Preprint / peer review** | A paper shared before / after expert review |
| **Ablation** | Removing a component to measure its contribution |
| **Baseline (research)** | The method you compare against |
| **Reproducibility** | Others can get the same result from your code and data |
| **State of the art (SOTA)** | The best reported result on a task, which may not generalise |
| **Scaling laws** | Observed patterns of how performance improves with model, data and compute |
| **Emergent ability** | A capability that appears only at larger scale (and is debated) |
| **Survey paper** | A paper summarising a whole subfield; a good starting point |
| **Negative result** | A finding that something does not work; valuable to report |
