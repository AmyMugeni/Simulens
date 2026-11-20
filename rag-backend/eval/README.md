# CogniLens RAG Evaluation Framework (RAGAS + IR Metrics)

This directory contains the automated evaluation framework for the CogniLens / SimuLens student digital wellbeing RAG assistant, designed to provide quantitative validation for the Final Year Project (FYP) dissertation and defense.

---

## 1. Evaluation Architecture

The evaluation evaluates two core stages of the system:
1. **Information Retrieval (IR) Stage**:
   - **Hit Rate@K**: Proportion of queries where at least one retrieved chunk matches the peer-reviewed target publication.
   - **Mean Reciprocal Rank (MRR)**: Average reciprocal rank of the first relevant chunk, rewarding systems that place high-signal literature at Rank 1.
   - **Average Cosine Similarity**: Quantitative embedding distance between query representation and chunk representations.

2. **Generation & Grounding (RAGAS LLM-as-a-Judge)**:
   - **Faithfulness (Grounding)**: Validates that every factual statement in the assistant's answer is directly backed by the retrieved context chunks (penalizes hallucinations).
   - **Answer Relevancy**: Assesses whether the generated response directly answers the student's question without extraneous drift.
   - **Context Precision**: Signal-to-noise ratio in the retrieved passages.
   - **Context Recall**: Whether all key factual points from the peer-reviewed ground truth are captured in the retrieved context.

---

## 2. Directory Structure

```
eval/
├── __init__.py
├── README.md               # Framework documentation and thesis usage guide
├── testset.json            # 16 curated golden test cases grounded in ingested papers
├── evaluate_rag.py         # Evaluation runner and export script
└── results/
    ├── ragas_summary.json  # Machine-readable per-query traces and scores
    ├── evaluation_table.md # Summary report for FYP documentation
    └── thesis_table.tex    # LaTeX table ready for thesis Chapter 4/5
```

---

## 3. Evaluation Dataset Categories

The 16 golden test queries in `testset.json` cover four core research pillars:
1. **Academic Focus (`academic_focus`)**: Attention residue, in-class notification checks, cognitive fragmentation, and study distraction.
2. **Sleep & Circadian Rhythm (`sleep_circadian`)**: Melatonin suppression from blue light, bedtime procrastination, psychological arousal, and sleep quality improvements.
3. **Habit Reinforcement (`habit_reinforcement`)**: Fear of Missing Out (FoMO), loneliness, upward social comparison, self-esteem, and techno-social stress.
4. **Actionable Strategies (`actionable_strategies`)**: Digital detox efficacy, micro-friction, 3-week randomized trial outcomes, and preventing rebound effects.

---

## 4. How to Run the Evaluation

### Quick Verification (1 sample per category)
```bash
python rag-backend/eval/evaluate_rag.py --quick
```

### Full Evaluation (Standard IR & Remote Generation)
```bash
python rag-backend/eval/evaluate_rag.py
```

### Full Evaluation with RAGAS LLM-as-a-Judge Scoring
To compute Faithfulness, Answer Relevancy, Context Precision, and Context Recall using a free Groq Llama-3.3-70b evaluator:
```bash
python rag-backend/eval/evaluate_rag.py --groq-key <YOUR_GROQ_API_KEY>
```
*(You can also export `GROQ_API_KEY` or `LLM_API_KEY` into your environment).*

---

## 5. Thesis Integration

After running the evaluation, three deliverables are generated in `eval/results/`:
- **`evaluation_table.md`**: Paste into project documentation or GitHub README.
- **`thesis_table.tex`**: Copy directly into `Chapter 4: Implementation and Results` or `Chapter 5: Evaluation and Discussion` of your FYP report.
- **`ragas_summary.json`**: Use for custom charts or visualizations.
