# CogniLens RAG Evaluation Report (RAGAS Framework)

*Evaluation Date: 2026-11-20 14:08:12*

## 1. Executive Summary

This automated evaluation validates the CogniLens / SimuLens student digital wellbeing Retrieval-Augmented Generation (RAG) assistant. Tests cover four research domains: **Academic Focus**, **Sleep & Circadian Rhythm**, **Habit Reinforcement**, and **Actionable Strategies**.

| Metric         |   Overall Score |
|----------------|-----------------|
| Total Samples  |         16      |
| Hit Rate@K     |          1      |
| MRR            |          0.8646 |
| Avg Cosine Sim |          0.7695 |


## 2. Category Performance Breakdown

| Category              |   Samples |   Hit Rate@K |      MRR |   Avg Cosine Sim |
|:----------------------|----------:|-------------:|---------:|-----------------:|
| academic_focus        |         4 |            1 | 0.875    |         0.73734  |
| actionable_strategies |         4 |            1 | 0.75     |         0.837436 |
| habit_reinforcement   |         4 |            1 | 1        |         0.793444 |
| sleep_circadian       |         4 |            1 | 0.833333 |         0.70974  |


## 3. Metric Interpretation & FYP Defense Benchmarks

- **Faithfulness (Grounding)**: Measures whether statements made by the LLM are backed by the scientific chunks. High score (>0.85) ensures student safety by preventing hallucinated health guidance.
- **Answer Relevance**: Measures whether the response directly addresses the query without off-topic drift.
- **Context Precision**: Evaluates the signal-to-noise ratio in the retrieved literature chunks.
- **Context Recall**: Verifies whether all reference ground truth facts from the peer-reviewed papers are retrieved.
- **Hit Rate@K & MRR**: Standard Information Retrieval (IR) metrics measuring whether the exact target publication is retrieved in the top K results.


## 4. Per-Query Evaluation Sample Breakdown

| ID          | Category              | Question                                         |   Top Cosine Score |   Target Hit |   Sources Cited |
|-------------|-----------------------|--------------------------------------------------|--------------------|--------------|-----------------|
| academic_01 | academic_focus        | How does using social media during study sess... |              0.764 |            1 |               1 |
| academic_02 | academic_focus        | Why do I feel mentally exhausted and distract... |              0.701 |            1 |               2 |
| academic_03 | academic_focus        | Does checking notifications during lectures h... |              0.653 |            1 |               2 |
| academic_04 | academic_focus        | How many hours of daily smartphone screen tim... |              0.831 |            1 |               4 |
| sleep_01    | sleep_circadian       | How does using my phone late at night disrupt... |              0.685 |            1 |               2 |
| sleep_02    | sleep_circadian       | Will reducing my screen time before bed actua... |              0.732 |            1 |               2 |
| sleep_03    | sleep_circadian       | Why do students stay up late scrolling social... |              0.692 |            1 |               3 |
| sleep_04    | sleep_circadian       | What physical and biological impacts does exc... |              0.729 |            1 |               4 |
| habit_01    | habit_reinforcement   | What is the psychological relationship betwee... |              0.783 |            1 |               4 |
| habit_02    | habit_reinforcement   | Can social media use increase feelings of lon... |              0.837 |            1 |               3 |
| habit_03    | habit_reinforcement   | What is techno-social stress and how does it ... |              0.765 |            1 |               4 |
| habit_04    | habit_reinforcement   | How does intensive social media consumption i... |              0.789 |            1 |               2 |
| strategy_01 | actionable_strategies | What actionable steps can I take to reduce my... |              0.797 |            1 |               3 |
| strategy_02 | actionable_strategies | Does a digital detox actually improve well-be... |              0.872 |            1 |               3 |
| strategy_03 | actionable_strategies | What mental health effects can I expect if I ... |              0.883 |            1 |               2 |
| strategy_04 | actionable_strategies | Why do people experience a rebound effect aft... |              0.797 |            1 |               4 |

