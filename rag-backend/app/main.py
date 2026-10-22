import hmac
import logging
import time
from collections import defaultdict, deque
from contextlib import asynccontextmanager
from datetime import date
from typing import Literal

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from pydantic import BaseModel, Field

from . import config, rag
from .safety import CRISIS_MESSAGE, is_crisis

# NOTE: message text is never logged, because it can contain sensitive personal information.
logging.basicConfig(level=logging.INFO)
log = logging.getLogger("cognilens-rag")


@asynccontextmanager
async def lifespan(app: FastAPI):
    rag.store.load()
    log.info("Loaded %d chunks", len(rag.store.chunks))
    try:
        from .embedder import embed_query

        embed_query("warm up")  # load the embedding model now, not on the first user request
    except Exception:  # pragma: no cover
        log.exception("Embedding model failed to load")
    yield


app = FastAPI(title="CogniLens RAG", lifespan=lifespan)


# ---- auth and rate limiting -------------------------------------------------

def require_token(x_app_token: str | None = Header(default=None)):
    if config.APP_TOKEN and not hmac.compare_digest(x_app_token or "", config.APP_TOKEN):
        raise HTTPException(status_code=401, detail="Unauthorized")


class RateLimiter:
    """In-memory, per client IP. Fine for one small instance; not shared between instances."""

    def __init__(self):
        self.minute: dict[str, deque] = defaultdict(deque)
        self.day: dict[str, tuple[str, int]] = {}

    def check(self, ip: str):
        now = time.time()
        q = self.minute[ip]
        while q and now - q[0] > 60:
            q.popleft()
        if len(q) >= config.RATE_PER_MINUTE:
            raise HTTPException(status_code=429, detail="Too many messages. Please wait a minute.")
        today = date.today().isoformat()
        d, count = self.day.get(ip, (today, 0))
        if d != today:
            count = 0
        if count >= config.RATE_PER_DAY:
            raise HTTPException(status_code=429, detail="Daily limit reached. Please try again tomorrow.")
        q.append(now)
        self.day[ip] = (today, count + 1)


limiter = RateLimiter()


def client_ip(request: Request) -> str:
    fwd = request.headers.get("x-forwarded-for")
    if fwd:
        return fwd.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


# ---- API --------------------------------------------------------------------

class Turn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(max_length=config.MAX_MESSAGE_CHARS)


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=config.MAX_MESSAGE_CHARS)
    history: list[Turn] = Field(default_factory=list, max_length=config.MAX_HISTORY_TURNS)
    # Short, consented summary built by the app, e.g. "BSMAS risk: moderate. Peak use 23:00-01:00."
    context: str = Field(default="", max_length=config.MAX_CONTEXT_CHARS)


class Source(BaseModel):
    n: int
    title: str
    url: str


class ChatResponse(BaseModel):
    answer: str
    sources: list[Source] = []
    mode: Literal["answer", "no_info", "crisis"]


@app.get("/health")
def health():
    return {"status": "ok", "chunks": len(rag.store.chunks)}


@app.post("/chat", response_model=ChatResponse, dependencies=[Depends(require_token)])
async def chat(req: ChatRequest, request: Request):
    limiter.check(client_ip(request))

    # 1) Safety first: no retrieval, no LLM.
    if is_crisis(req.message):
        return ChatResponse(answer=CRISIS_MESSAGE, mode="crisis")

    history = [t.model_dump() for t in req.history]

    # 2) Retrieve. If nothing relevant, say so instead of letting the model improvise.
    hits = rag.retrieve(req.message, history)
    if not hits:
        return ChatResponse(answer=rag.NO_INFO_MESSAGE, mode="no_info")

    # 3) Generate from the retrieved sources only.
    try:
        answer = await rag.call_llm(rag.build_messages(req.message, history, req.context, hits))
    except httpx.HTTPStatusError as e:
        log.warning("LLM provider returned %s", e.response.status_code)
        if e.response.status_code == 429:
            raise HTTPException(status_code=503, detail="The assistant is busy right now. Please try again shortly.")
        raise HTTPException(status_code=502, detail="The assistant is unavailable right now.")
    except (httpx.HTTPError, RuntimeError):
        log.exception("LLM call failed")
        raise HTTPException(status_code=502, detail="The assistant is unavailable right now.")

    return ChatResponse(
        answer=answer,
        sources=[Source(**s) for s in rag.cited_sources(answer, hits)],
        mode="answer",
    )
