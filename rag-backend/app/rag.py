import json
import re
from collections import Counter

import httpx
import numpy as np

from . import config
from .embedder import embed_query

NO_INFO_MESSAGE = (
    "I don't have reliable information on that in my sources, so I'd rather not guess. "
    "You could try rephrasing the question, or do a global search in your web browser."
)

SYSTEM_PROMPT = """You are SimuLens Assistant, a supportive guide inside a student wellbeing app about social media use.

Rules:
- Strict Scope & Grounding: You ONLY answer questions about social media use, digital wellbeing, sleep, attention/academic focus, mental health, or the user's personal SimuLens data in USER CONTEXT. If the question is off-topic (e.g., cooking recipes like ugali, general trivia, coding, or unrelated tasks) or is not supported by SOURCES or USER CONTEXT, refuse politely using: "I don't have reliable information on that in my sources, so I'd rather not guess. I'm specifically designed to help with your digital wellbeing and social media habits."
- Research Questions: Answer health, psychological, and wellbeing research questions using the numbered SOURCES. Cite sources inline like [1] or [2].
- Coping & Behavioral Support: When students ask how to cope with stress, emotional burnout, or the impulse to escape into social media feeds, guide them with compassionate, evidence-based coping mechanisms and behavioral self-regulation techniques grounded in the literature (e.g. micro-friction, intentional bedtime boundaries, mindful pause, and fulfilling offline replacement activities).
- Emergency & Safety Context: You serve Kenyan university students. Only ever provide Kenyan emergency and mental health resources: Befrienders Kenya (+254 722 178 177), Kenya Red Cross Helpline (1199), or Kenya Emergency Services (999 / 112). Never cite foreign or North American hotlines.
- User Data & Personal Questions: If the user asks what you know about them or asks about their data/score/schedule, summarize what is provided in USER CONTEXT in a clear, friendly way.
- No Invented Facts: Never invent personal details not present in USER CONTEXT.
- Tone & Boundaries: Be warm, plain-language and non-judgmental. Never diagnose, and never tell the user they are "addicted".
- Response Completeness & Length: Provide clear, fully finished answers. For general questions, write a thorough yet accessible explanation (around 200 to 350 words). If the user asks for a plan, routine, coping mechanisms, or strategies, provide a detailed, actionable numbered list of 3 to 5 concrete steps (around 300 to 500 words), tie them directly to USER CONTEXT when available, and cite supporting evidence for each recommendation. Never cut off your response mid-thought or mid-sentence.
- Citing Sources: Only cite [1], [2] when referencing research findings from the provided SOURCES. Do not attach source numbers to personal context summaries."""


class Store:
    def __init__(self):
        self.chunks: list[dict] = []
        self.matrix: np.ndarray | None = None

    def load(self):
        chunks_path = config.DATA_DIR / "chunks.json"
        index_path = config.DATA_DIR / "index.npz"
        if not chunks_path.exists() or not index_path.exists():
            raise RuntimeError(
                "Knowledge base not found. Run `python -m scripts.ingest` and commit the data/ folder."
            )
        self.chunks = json.loads(chunks_path.read_text(encoding="utf-8"))
        self.matrix = np.load(index_path)["embeddings"].astype(np.float32)
        if len(self.chunks) != len(self.matrix):
            raise RuntimeError("chunks.json and index.npz are out of sync. Re-run ingestion.")

    def search(self, qvec: np.ndarray, k: int, max_per_source: int = 2):
        scores = self.matrix @ qvec
        order = np.argsort(-scores)
        picked, per_source = [], Counter()
        for i in order:
            chunk = self.chunks[int(i)]
            if per_source[chunk["url"]] >= max_per_source:
                continue
            per_source[chunk["url"]] += 1
            picked.append((chunk, float(scores[i])))
            if len(picked) >= k:
                break
        return picked


store = Store()


PLAN_REQUEST = re.compile(
    r"\b(plan|routine|strateg(?:y|ies)|action plan|actionable|steps|tips|advice|cut down|reduce|help me|recommendations?|coping|coping strategies|escape|stress|stress relief|alternatives?|habit|detox)\b",
    re.IGNORECASE
)
PLAN_HINT = " evidence-based practical strategies, behavioral interventions, digital detox, healthier coping mechanisms, stress management, and self-regulation techniques to reduce social media and screen time"


def clean_query_text(text: str) -> str:
    """Fix common repeated-letter typos and normalize search terms."""
    # e.g., 'strressed' -> 'stressed', 'scrrreen' -> 'screen'
    t = re.sub(r"\bstr+es+ed\b", "stressed", text, flags=re.IGNORECASE)
    t = re.sub(r"\bscr+e+n\b", "screen", t, flags=re.IGNORECASE)
    t = re.sub(r"\bsoc+i+al\b", "social", t, flags=re.IGNORECASE)
    return t


def retrieval_query(message: str, history: list[dict]) -> str:
    """Short follow-ups like 'why?' retrieve badly alone, so borrow the previous user question.
    Plan and coping requests ('what are healthier coping strategies') benefit from topic words
    that pull in the behavioral intervention and detox literature."""
    query = clean_query_text(message)
    if len(query.split()) < 8:
        prev = [h["content"] for h in history if h["role"] == "user"]
        if prev:
            query = f"{prev[-1]} {query}"
    if PLAN_REQUEST.search(query):
        query += PLAN_HINT
    return query


def retrieve(message: str, history: list[dict]):
    qvec = embed_query(retrieval_query(message, history))
    hits = store.search(qvec, config.TOP_K)
    return [(c, s) for c, s in hits if s >= config.MIN_SCORE]


def group_hits_by_paper(hits):
    """Group multiple retrieved chunks from the same paper into a single source entry."""
    papers = []
    seen = {}
    for chunk, score in hits:
        key = chunk.get("url") or chunk.get("title")
        if key in seen:
            seen[key]["texts"].append(chunk["text"])
            seen[key]["scores"].append(score)
        else:
            entry = {
                "title": chunk["title"],
                "url": chunk["url"],
                "texts": [chunk["text"]],
                "scores": [score],
            }
            seen[key] = entry
            papers.append(entry)
    return papers


def build_messages(message: str, history: list[dict], context: str, hits) -> list[dict]:
    papers = group_hits_by_paper(hits)
    source_blocks = []
    for n, p in enumerate(papers, 1):
        combined_text = "\n\n".join(p["texts"])
        source_blocks.append(f"[{n}] {p['title']} ({p['url']})\n{combined_text}")
    sources = "\n\n".join(source_blocks)
    user_block = (
        f"USER CONTEXT:\n{context or 'none provided'}\n\n"
        f"SOURCES:\n{sources}\n\n"
        f"QUESTION: {message}"
    )
    messages = [{"role": "system", "content": SYSTEM_PROMPT}]
    for h in history[-config.MAX_HISTORY_TURNS:]:
        messages.append({"role": h["role"], "content": h["content"][: config.MAX_MESSAGE_CHARS]})
    messages.append({"role": "user", "content": user_block})
    return messages


async def call_llm(messages: list[dict]) -> str:
    if not config.LLM_API_KEY:
        raise RuntimeError("LLM_API_KEY is not set on the server.")
    async with httpx.AsyncClient(timeout=45) as client:
        r = await client.post(
            f"{config.LLM_BASE_URL}/chat/completions",
            headers={"Authorization": f"Bearer {config.LLM_API_KEY}"},
            json={
                "model": config.LLM_MODEL,
                "messages": messages,
                "temperature": 0.3,
                "max_tokens": 1500,
            },
        )
    r.raise_for_status()
    text = (r.json()["choices"][0]["message"].get("content") or "").strip()
    if not text:
        raise RuntimeError("The model returned an empty answer.")
    return text


def cited_sources(answer: str, hits) -> list[dict]:
    """Return only the sources the answer explicitly cites inline (e.g. [1], [2])."""
    nums = {int(n) for n in re.findall(r"\[(\d+)\]", answer)}
    papers = group_hits_by_paper(hits)
    out = []
    for n, p in enumerate(papers, 1):
        if n in nums:
            out.append({"n": n, "title": p["title"], "url": p["url"]})
    return out
