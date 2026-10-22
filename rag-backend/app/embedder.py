import numpy as np

from .config import EMBED_MODEL, MODEL_CACHE, QUERY_PREFIX

_model = None


def _get():
    global _model
    if _model is None:
        from fastembed import TextEmbedding  # imported lazily so tests can run without it

        MODEL_CACHE.mkdir(exist_ok=True)
        _model = TextEmbedding(model_name=EMBED_MODEL, cache_dir=str(MODEL_CACHE))
    return _model


def _normalize(m: np.ndarray) -> np.ndarray:
    norms = np.linalg.norm(m, axis=-1, keepdims=True)
    norms[norms == 0] = 1.0
    return m / norms


def embed_passages(texts: list[str]) -> np.ndarray:
    vecs = np.array(list(_get().embed(texts)), dtype=np.float32)
    return _normalize(vecs)


def embed_query(text: str) -> np.ndarray:
    vec = np.array(list(_get().embed([QUERY_PREFIX + text]))[0], dtype=np.float32)
    return _normalize(vec)
