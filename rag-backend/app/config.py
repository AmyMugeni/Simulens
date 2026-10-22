import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"
MODEL_CACHE = BASE_DIR / ".model_cache"

# Small, free, runs on CPU. Used for both ingestion and queries, so never change one without the other.
EMBED_MODEL = "BAAI/bge-small-en-v1.5"
QUERY_PREFIX = "Represent this sentence for searching relevant passages: "

LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.groq.com/openai/v1").rstrip("/")
LLM_MODEL = os.getenv("LLM_MODEL", "llama-3.3-70b-versatile")

APP_TOKEN = os.getenv("APP_TOKEN", "")

TOP_K = int(os.getenv("TOP_K", "4"))
MIN_SCORE = float(os.getenv("MIN_SCORE", "0.45"))

RATE_PER_MINUTE = int(os.getenv("RATE_PER_MINUTE", "12"))
RATE_PER_DAY = int(os.getenv("RATE_PER_DAY", "150"))

MAX_MESSAGE_CHARS = 1000
MAX_CONTEXT_CHARS = 600
MAX_HISTORY_TURNS = 6
