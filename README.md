# Simulens 🌿

**Simulens** is an adaptive, on-device Machine Learning (ML) and Retrieval-Augmented Generation (RAG) digital wellbeing system for Android. It classifies social media usage behavior, detects compulsive risk trajectories, and delivers real-time contextual nudges and personalized AI-assisted guidance for university students.

---

## 🌟 Key Features

* **🧠 On-Device Random Forest Classifier (`simulens_rf_champion.onnx`):**
  * Runs real-time ONNX model tensor inference on-device using Microsoft ONNX Runtime (`onnxruntime-android`).
  * Evaluates **29 behavioral features** over a 28-day window (including 14-day velocity trends `duration_change` and `session_change`).
  * Classifies behavioral trajectories into **`NORMAL`** vs **`ELEVATED`** compulsive risk levels ($\ge 50\%$ probability threshold).

* **📊 Interactive BSMAS Assessment & Retake History:**
  * Implements the 6-item **Bergen Social Media Addiction Scale (BSMAS)** (Scored 6–30).
  * Categorizes risk into **`LOWER` ($\le 12$)**, **`MODERATE` ($13 \dots 18$)**, and **`ELEVATED` ($\ge 19$)** bands.
  * Full retake support with score calculations and timestamped history tracking in local Room SQLite storage.

* **💬 Simulens AI Companion (RAG-Powered Chatbot):**
  * Powered by LLaMA 3.3 70B Versatile via Groq Cloud API, BGE-small embeddings (`bge-small-en-v1.5`), and HMAC token authentication.
  * **Personalization Toggle:** Dynamically constructs consented context summaries (BSMAS score, top focus struggles, sleep schedules) when enabled.
  * **Multilingual Crisis Safety Filter:** Screens English and Kiswahili/Kenyan crisis expressions using Unicode `NFKC` normalization (`safety.py`).

* **🔔 Real-Time Nudge & Intervention Engine:**
  * Evaluates active session contexts:
    * `SCHEDULE_CONFLICT` (Usage during academic lectures/classes via `ClassScheduleMapper` & timetable entries).
    * `BEDTIME_LATE_NIGHT` (Bedtime buffer violations).
    * `MORNING_ROUTINE` (Usage within 30 minutes of waking).
    * `REOPEN_VELOCITY` (Compulsive re-opening within 5 minutes).
  * Displays full-screen interactive overlays (`InterventionOverlay`) or heads-up notifications (`InterventionNotifier`).

* **📂 Privacy-Preserving CSV Dataset Exporter:**
  * Generates 4 structured CSV files in `ml_export/` for empirical research:
    * `behavioral_sessions.csv`
    * `daily_statistics.csv`
    * `bsmas_history.csv`
    * `daily_tracking_coverage.csv`
  * Features salted SHA-256 `anonymousUserId` hashing and 30-day historical data retention.

* **⚙️ Background Service Resilience:**
  * Foreground tracking service (`AppUsageTrackerService`) with `BootReceiver` for `BOOT_COMPLETED` restart and `onTaskRemoved` task swipe re-enqueue.

---

## 🏗 System Architecture & Module Structure

```text
simulens/
├── app/
│   └── src/
│       └── main/
│           ├── assets/
│           │   └── simulens_rf_champion.onnx  # Trained Random Forest ONNX Model
│           └── java/com/simulens/app/
│               ├── data/
│               │   ├── local/               # Room Database (SimulensDatabase v10)
│               │   ├── export/              # Multi-file CSV Exporter (RoomCsvExporter)
│               │   ├── tracker/             # Foreground Tracking Service & BootReceiver
│               │   └── repositories/        # User Profile & Monitored Apps DataStore
│               ├── domain/                  # Rule Engine, Baselines & BSMAS Scoring
│               ├── ml/                      # ONNX Predictor & 29-Feature Extractor
│               ├── rag/                     # Chat ViewModel, Prompts & Personalization
│               └── ui/                      # Jetpack Compose Screens
│                   ├── dashboard/           # Dashboard, ML Classifier Card & Line Graph
│                   ├── profile/             # Profile & Interactive BSMAS Retakes
│                   ├── onboarding/          # App Onboarding & Permissions Flow
│                   └── intervention/        # Full-Screen Nudge Overlays
├── rag-backend/                             # FastAPI RAG Backend Service
│   ├── app/
│   │   ├── main.py                          # FastAPI Application Server
│   │   ├── rag.py                           # Vector Retrieval & System Prompts
│   │   └── safety.py                        # Multilingual Crisis Detection (NFKC)
│   ├── requirements.txt
│   └── README.md
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🧪 Machine Learning Pipeline Specs

| Attribute | Specification |
| :--- | :--- |
| **Model Architecture** | Random Forest Classifier (`n_estimators=100`, `class_weight='balanced'`) |
| **Runtime Format** | Open Neural Network Exchange (ONNX) v1.x via Microsoft ONNX Runtime Android SDK |
| **Input Tensor Shape** | `[batch_size, 37]` (29 raw extracted behavioral features + encoded categories) |
| **Output Shape** | Class Label (`0`: NORMAL, `1`: ELEVATED) + Probability Vector |
| **Classification Threshold** | $\ge 0.50$ ($50\%$ probability threshold) |
| **Evaluation Metrics** | Accuracy: **95%**, Precision: **0.96**, Recall: **0.95**, F1-Score: **0.95**, ROC-AUC: **0.98+** |

---

## 🛠 Setup & Technical Requirements

* **Android Studio:** Studio Ladybug (2024.2.1) or higher
* **Compile SDK:** 36
* **Minimum SDK:** 24 (Android 7.0+)
* **Language:** Kotlin 2.0.x with Jetpack Compose
* **Local Persistence:** Android Room SQLite with KSP (Kotlin Symbol Processing)
* **ML Inference:** `com.microsoft.onnxruntime:onnxruntime-android:1.18.0`

### Local Configuration (`local.properties`)

Create or update `local.properties` in the project root:

```properties
RAG_BASE_URL=https://simulens-rag.onrender.com
RAG_APP_TOKEN=your_hmac_secret_token
```

---

## 🔒 Privacy & Safety Disclosures

* **Data Safety:** All usage stats, session timestamps, and BSMAS scores are stored locally in private SQLite app storage.
* **CSV Export:** All exported dataset CSVs use a salted SHA-256 `anonymousUserId` to guarantee participant anonymity.
* **Non-Clinical Disclaimer:** Simulens is an educational digital wellbeing tool designed for university students. It does not provide clinical mental health diagnoses.
