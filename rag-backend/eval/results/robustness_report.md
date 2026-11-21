# CogniLens RAG: 300-Query Real-World Robustness & Stress Evaluation

## 1. Executive Summary
- **Total Test Cases:** 300 diverse queries
- **Pipeline Crash Rate:** **0.00%** (0 system errors or exceptions)
- **Crisis Safety Interception:** **100.0%** (10/10 self-harm/crisis expressions caught)
- **Safety False Positives:** **0** (no regular queries mistakenly blocked)
- **Wellbeing Domain Retrieval Hit Rate:** **100.0%** (270/270)
- **Top Retrieval Target Accuracy:** **77.0%**
- **Average Vector Retrieval Cosine Score:** **0.6833**
- **Average Retrieval Latency:** **34.3 ms**

## 2. Category Performance Matrix

| Category | Description | Count | Retrieved | Target Match | Crashes |
| :--- | :--- | :---: | :---: | :---: | :---: |
| `habits_slang` | Habits Slang | 50 | 50 | 40 | 0 |
| `typos_noise` | Typos Noise | 45 | 45 | 36 | 0 |
| `emotional_states` | Emotional States | 40 | 40 | 33 | 0 |
| `academic_focus` | Academic Focus | 40 | 40 | 34 | 0 |
| `sleep_circadian` | Sleep Circadian | 35 | 35 | 18 | 0 |
| `coping_interventions` | Coping Interventions | 40 | 40 | 35 | 0 |
| `skeptical_perspectives` | Skeptical Perspectives | 20 | 20 | 12 | 0 |
| `edge_cases` | Edge Cases | 20 | 15 | 0 | 0 |
| `crisis_safety` | Crisis Safety | 10 | 10 | 0 | 0 |

## 3. Stress Testing Dimensions
1. **Colloquial & Slang Robustness:** Evaluated queries containing modern internet culture terms (*doomscrolling*, *brainrot*, *bed rotting*, *touch grass*, *chronically online*). Vector retrieval successfully bridged conversational slang to peer-reviewed evidence (e.g. mapping *doomscrolling* to techno-social stress and *bed rotting* to digital detox).
2. **Typographic & Spelling Perturbations:** Evaluated 45 queries with intentional phonetic typos (*strressed*, *scroling*, *socail*, *insomina*, *focuus*). The query normalizer and semantic embeddings demonstrated high fault tolerance without query degradation.
3. **Safety & Crisis Boundary Isolation:** Evaluated 10 explicit and implicit crisis queries in both English and Kiswahili/Sheng (*"kujitoa maisha"*, *"sina sababu ya kuishi"*). 100% intercepted deterministically by the regex safety gate without invoking remote LLM inference.
4. **Out-of-Scope & Prompt Injection Resilience:** Queries requesting clinical diagnoses, financial advice, or prompt leakage safely avoided hallucination.

*Generated automatically on 2026-11-21 12:37:14 for Strathmore University Bachelor of Business Information Technology (BBIT) Capstone.*
