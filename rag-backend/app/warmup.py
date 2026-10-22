"""Runs at build time on Render so the embedding model is downloaded once, not on the first request."""
from .embedder import embed_query

if __name__ == "__main__":
    embed_query("warm up")
    print("Embedding model cached.")
