"""Build the knowledge base from references.txt. Run on your laptop, then commit the data/ folder.

    pip install -r requirements-ingest.txt
    python -m scripts.ingest

references.txt format: one link per line, optionally "URL | Title". Lines starting with # are ignored.
Works with web pages (HTML) and direct PDF links. Papers behind a paywall or login can't be fetched:
download the PDF yourself, put it in the pdfs/ folder, and it will be ingested too.
"""
import io
import json
import re
import sys
from pathlib import Path

import numpy as np
import requests

from app.config import DATA_DIR
from app.embedder import embed_passages

ROOT = Path(__file__).resolve().parent.parent
REFS = ROOT / "references.txt"
PDF_DIR = ROOT / "pdfs"

CHUNK_WORDS = 280
OVERLAP_WORDS = 50
MIN_CHUNK_WORDS = 40
HEADERS = {"User-Agent": "Mozilla/5.0 (CogniLens-student-project ingestion script)"}

# Everything from one of these headings onward is bibliography or boilerplate, not content.
CUT_HEADINGS = re.compile(
    r"^\s*(references|bibliography|works cited|acknowledg(e)?ments|conflicts? of interest|"
    r"declaration of interest|funding|author contributions|supplementary (material|data))\s*:?\s*$",
    re.IGNORECASE | re.MULTILINE,
)


def read_refs() -> list[tuple[str, str | None]]:
    refs = []
    for line in REFS.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        url, _, title = line.partition("|")
        refs.append((url.strip(), title.strip() or None))
    return refs


def clean(text: str) -> str:
    m = None
    # Only cut at a heading in the back half, so a mention of "References" early on doesn't truncate the paper.
    for match in CUT_HEADINGS.finditer(text):
        if match.start() > len(text) * 0.4:
            m = match
            break
    if m:
        text = text[: m.start()]
    text = re.sub(r"-\n(\w)", r"\1", text)          # re-join hyphenated line breaks
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = re.sub(r"[ \t]+", " ", text)
    return text.strip()


def pdf_to_text(data: bytes) -> tuple[str, str | None]:
    from pypdf import PdfReader

    reader = PdfReader(io.BytesIO(data))
    pages = [(p.extract_text() or "") for p in reader.pages]
    title = (reader.metadata.title or "").strip() if reader.metadata else None
    return "\n\n".join(pages), title or None


def html_to_text(html: str) -> tuple[str, str | None]:
    import trafilatura

    text = trafilatura.extract(html, include_comments=False, include_tables=False) or ""
    meta = trafilatura.extract_metadata(html)
    return text, (meta.title if meta and meta.title else None)


def fetch(url: str) -> tuple[str, str | None]:
    r = requests.get(url, headers=HEADERS, timeout=40)
    r.raise_for_status()
    is_pdf = url.lower().split("?")[0].endswith(".pdf") or "application/pdf" in r.headers.get("content-type", "")
    return pdf_to_text(r.content) if is_pdf else html_to_text(r.text)


def chunk(text: str) -> list[str]:
    """Pack paragraphs into ~CHUNK_WORDS-word chunks with a small overlap."""
    words_in = lambda s: len(s.split())
    paras = [p.strip().replace("\n", " ") for p in text.split("\n\n") if words_in(p) >= 8]
    chunks, cur = [], []
    for p in paras:
        if cur and sum(words_in(x) for x in cur) + words_in(p) > CHUNK_WORDS:
            chunks.append(" ".join(cur))
            tail = " ".join(" ".join(cur).split()[-OVERLAP_WORDS:])
            cur = [tail]
        cur.append(p)
    if cur:
        chunks.append(" ".join(cur))
    # Very long single paragraphs (common in PDFs with lost line breaks): split by words.
    out = []
    for c in chunks:
        w = c.split()
        if len(w) <= CHUNK_WORDS * 1.5:
            out.append(c)
        else:
            step = CHUNK_WORDS - OVERLAP_WORDS
            out += [" ".join(w[i : i + CHUNK_WORDS]) for i in range(0, len(w), step)]
    return [c for c in out if words_in(c) >= MIN_CHUNK_WORDS]


def main():
    sources: list[tuple[str, str, str]] = []  # (title, url, text)

    if REFS.exists():
        for url, given_title in read_refs():
            try:
                text, found_title = fetch(url)
                text = clean(text)
                if len(text.split()) < 150:
                    print(f"SKIP (too little text, maybe paywalled or scanned): {url}")
                    continue
                sources.append((given_title or found_title or url, url, text))
                print(f"OK   {len(text.split()):>6} words  {url}")
            except Exception as e:
                print(f"FAIL {url}  ({e})")
    else:
        print(f"No {REFS.name} found.")

    if PDF_DIR.exists():
        for pdf in sorted(PDF_DIR.glob("*.pdf")):
            try:
                text, meta_title = pdf_to_text(pdf.read_bytes())
                text = clean(text)
                # No public link for a local file: put the real link in the filename-mapped refs file if you have one.
                sources.append((meta_title or pdf.stem.replace("_", " "), f"file:{pdf.name}", text))
                print(f"OK   {len(text.split()):>6} words  {pdf.name}")
            except Exception as e:
                print(f"FAIL {pdf.name}  ({e})")

    if not sources:
        sys.exit("Nothing ingested. Add links to references.txt or PDFs to pdfs/.")

    chunks = []
    for title, url, text in sources:
        for i, c in enumerate(chunk(text)):
            chunks.append({"id": len(chunks), "title": title[:200], "url": url, "text": c})

    print(f"\nEmbedding {len(chunks)} chunks from {len(sources)} sources...")
    matrix = embed_passages([c["text"] for c in chunks])

    DATA_DIR.mkdir(exist_ok=True)
    (DATA_DIR / "chunks.json").write_text(json.dumps(chunks, ensure_ascii=False), encoding="utf-8")
    np.savez_compressed(DATA_DIR / "index.npz", embeddings=matrix.astype(np.float16))
    print(f"Saved {len(chunks)} chunks to {DATA_DIR}. Commit the data/ folder and deploy.")


if __name__ == "__main__":
    main()
