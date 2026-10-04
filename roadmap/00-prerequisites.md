# 0. Prerequisites

What to be comfortable with **before** the AI branches, how deep you need to go, and how to check yourself. You do not need a
maths degree. You do need enough to read a formula without panic and write code that works.

[← Back to the roadmap](../README.md) · Next: [1. Machine Learning](01-machine-learning.md)

## What you need, by goal

| Area | To **build with AI** (apps, chatbots, agents) | To **do ML and data science** | To **do research** |
|------|:--:|:--:|:--:|
| Python | Solid | Solid | Solid |
| Git, command line, APIs | Solid | Solid | Solid |
| SQL | Basic | Solid | Basic |
| Statistics and probability | Intuition | Solid | Strong |
| Linear algebra | Intuition | Working | Strong |
| Calculus | Skip at first | Working (derivatives, gradients) | Strong |
| Algorithms and data structures | Basic | Working | Strong |
| Reading English technical text | Solid | Solid | Solid |

*Intuition* = you can explain the idea in words and know where to look the details up. *Working* = you can do the calculations on
small examples. *Strong* = you can derive and prove.

## The topics

### Programming (start here, everyone)

| Topic | What to learn | Check yourself |
|-------|---------------|----------------|
| Python basics | Types, functions, classes, modules, virtual environments, exceptions, file I/O | You can write a 100-line script that reads a CSV, summarises it and writes a result |
| Python for data | NumPy arrays and broadcasting; pandas DataFrames; plotting with Matplotlib | You can load a table, clean missing values, group, merge and plot |
| Git and GitHub | Commit, branch, merge, pull request | You can recover a file you deleted two commits ago |
| Command line | Navigation, pipes, environment variables | You can run a script with arguments and read its logs |
| HTTP and APIs | REST, JSON, authentication, rate limits, `requests` | You can call a public API and handle an error response |
| Jupyter / notebooks | Cells, restarting kernels, why notebooks hide bugs | You can reproduce a result by "Restart and run all" |
| Testing and debugging | Unit tests, reading stack traces, logging | You write a test before fixing a bug |
| SQL | SELECT, WHERE, JOIN, GROUP BY, window functions, indexes | You can answer a business question with one query |

*Other languages:* Python dominates AI work. Java, JavaScript/TypeScript and others are common for **building products around
models** (calling APIs, serving, UIs) and can be a starting point if that is your background; you will still read Python.

### Mathematics

| Topic | What matters for AI | Check yourself |
|-------|---------------------|----------------|
| **Linear algebra** | Vectors, matrices, dot product, matrix multiplication, transpose, inverse, eigenvalues, SVD. Data and neural-network weights are matrices. | You can say what `A @ B` does to shapes `(m,n)` and `(n,k)` |
| **Calculus** | Derivative, partial derivative, chain rule, gradient. The chain rule *is* backpropagation. | You can differentiate `(wx - y)^2` with respect to `w` |
| **Probability** | Random variables, distributions (normal, binomial), conditional probability, Bayes' rule, expectation, variance, independence | You can explain why a 99%-accurate test can still be wrong most of the time for a rare disease |
| **Statistics** | Sampling, mean/median, standard deviation, correlation vs causation, confidence intervals, hypothesis tests, p-values and their limits | You can explain what a 95% confidence interval does and does not say |
| **Optimisation** | Loss functions, gradient descent, learning rate, local vs global minimum, convexity (intuition) | You can run gradient descent by hand for three steps |
| **Information theory** (later) | Entropy, cross-entropy, KL divergence: the basis of the usual loss functions | You know why cross-entropy is used for classification |

### Computer science

| Topic | What to learn | Check yourself |
|-------|---------------|----------------|
| Complexity | Big-O for time and memory | You can say why a nested loop over 1M items is a problem |
| Data structures | Arrays, hash maps, sets, stacks, queues, heaps, trees, graphs | You pick a hash map for lookup without thinking |
| Core algorithms | Sorting, binary search, BFS/DFS, recursion, basic dynamic programming | You can solve easy-level problems on a coding site |
| How computers work | Memory, CPU vs GPU, files, processes, networks (basics) | You can explain why a GPU helps matrix maths |

See [05-algorithms](05-algorithms.md) for the AI-specific algorithm atlas.

### Tools to set up

| Tool | Purpose |
|------|---------|
| Python 3.11+ with `venv` or `uv`/`conda` | Isolated environments |
| VS Code or PyCharm, plus Jupyter | Editor and notebooks |
| Git and a GitHub account | Version control and a portfolio |
| A free GPU notebook (Google Colab, Kaggle Notebooks) | Deep learning without buying hardware |
| Docker (later) | Reproducible environments, deployment |
| An LLM chat assistant | A tutor for questions; verify what it tells you |

## Suggested study order and time

1. Python basics, then NumPy/pandas (3 to 5 weeks)
2. Git and the command line (1 week, alongside)
3. SQL basics (1 to 2 weeks)
4. Statistics and probability intuition, then linear algebra and calculus intuition (4 to 6 weeks, alongside coding)

Estimates at about 8 hours a week; faster if you already program.

## Resources

See [resources.md](../resources/resources.md#0-prerequisites) for the specific courses and books.

## Done when

- You can write, run and debug a Python program of a few hundred lines without copying it.
- You can load a dataset with pandas, clean it and plot it.
- You can explain gradient, probability distribution, matrix multiplication and Bayes' rule in plain words.
- You have a GitHub account with at least one project in it.

## Common mistakes

- **Studying maths for months before writing any code.** Learn maths in context: when a model needs the gradient, learn gradients then.
- **Skipping Python fundamentals** for frameworks. Most ML bugs are ordinary programming bugs (shapes, indexing, state).
- **Only watching.** If you cannot do the exercise without the video, you have not learned it yet.
- **Trusting an AI tutor blindly.** Use it to explain, then check the answer by running code or reading a reference.
