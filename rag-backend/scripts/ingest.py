"""Build the knowledge base from references.txt and local PDFs.

    pip install -r requirements-ingest.txt
    python -m scripts.ingest
"""
import io
import json
import re
import sys
from pathlib import Path

import numpy as np
import requests

from app.config import DATA_DIR
from app.embedder import _get, _normalize

ROOT = Path(__file__).resolve().parent.parent
REFS = ROOT / "references.txt"
PDF_DIR = ROOT / "data"

CHUNK_WORDS = 280
OVERLAP_WORDS = 50
MIN_CHUNK_WORDS = 40
HEADERS = {"User-Agent": "Mozilla/5.0 (CogniLens-student-project ingestion script)"}

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
    for match in CUT_HEADINGS.finditer(text):
        if match.start() > len(text) * 0.4:
            m = match
            break
    if m:
        text = text[: m.start()]
    text = re.sub(r"-\n(\w)", r"\1", text)
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
    r = requests.get(url, headers=HEADERS, timeout=5)
    r.raise_for_status()
    is_pdf = url.lower().split("?")[0].endswith(".pdf") or "application/pdf" in r.headers.get("content-type", "")
    return pdf_to_text(r.content) if is_pdf else html_to_text(r.text)

def chunk(text: str) -> list[str]:
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
    out = []
    for c in chunks:
        w = c.split()
        if len(w) <= CHUNK_WORDS * 1.5:
            out.append(c)
        else:
            step = CHUNK_WORDS - OVERLAP_WORDS
            out += [" ".join(w[i : i + CHUNK_WORDS]) for i in range(0, len(w), step)]
    return [c for c in out if words_in(c) >= MIN_CHUNK_WORDS]

PDF_METADATA = {
    "2019-Cyberpsychology-Socialmediaaddiction.pdf": (
        "Hou et al. (2019) Social media addiction: Its impact, mediation, and intervention (Cyberpsychology)",
        "https://doi.org/10.5817/CP2019-1-4"
    ),
    "Flayelleetal_NRP_2023.pdf": (
        "Flayelle et al. (2023) A taxonomy of technology design features promoting addictive online behaviours (Nature Reviews Psychology)",
        "https://doi.org/10.1038/s44159-023-00153-4"
    ),
    "He_2014_PsychologicalReports.pdf": (
        "Turel et al. (2014) Examination of neural systems sub-serving social media and Facebook 'addiction' (Psychological Reports)",
        "https://doi.org/10.2466/18.PR0.115c31z8"
    ),
    "nudge-smartphone.pdf": (
        "Olson et al. (2022) A nudge-based intervention to reduce problematic smartphone use: Randomised controlled trial (IJMA)",
        "https://doi.org/10.1007/s11469-022-00826-w"
    ),
    "On_Social_Media_Design_Online-Time_Well-spent_and_.pdf": (
        "Montag & Elhai (2023) On Social Media Design, (Online-)Time Well-spent and Addictive Behaviors (Current Addiction Reports)",
        "https://doi.org/10.1007/s40429-023-00494-3"
    ),
    "SocialMediaDisorderScale.pdf": (
        "van den Eijnden et al. (2016) The Social Media Disorder Scale: Validity and psychometric properties (Computers in Human Behavior)",
        "https://doi.org/10.1016/j.chb.2016.03.038"
    ),
    "Social_Media_Internet_and_Electronic_Devices_Addic.pdf": (
        "Barmada (2020) Social Media, Internet and Electronic Devices Addiction among Children and Adolescents (JPRRR)",
        "https://doi.org/10.47363/JPRRR/2020(2)109"
    ),
    "Social_Media_Use_Engagement_and_Addiction_as_Predi.pdf": (
        "Al-Menayes (2015) Social Media Use, Engagement and Addiction as Predictors of Academic Performance (IJPS)",
        "https://doi.org/10.5539/ijps.v7n4p86"
    ),
    "What Makes social media so Addictive and How Does it Affect our.pdf": (
        "Stangler (2024) What Makes Social Media so Addictive, and How Does it Affect our Mental Health? (UND)",
        "https://commons.und.edu/psych-stu/2"
    )
}

def main():
    sources: list[tuple[str, str, str]] = []

    # 1. Local PDF Files in data/
    if PDF_DIR.exists():
        for pdf in sorted(PDF_DIR.glob("*.pdf")):
            try:
                text, meta_title = pdf_to_text(pdf.read_bytes())
                text = clean(text)
                if len(text.split()) < 100:
                    continue
                title, url = PDF_METADATA.get(
                    pdf.name,
                    (meta_title or pdf.stem.replace("_", " "), f"https://scholar.google.com/scholar?q={pdf.stem}")
                )
                sources.append((title, url, text))
                print(f"OK   {len(text.split()):>6} words  {pdf.name} -> {title[:60]}")
            except Exception as e:
                print(f"FAIL {pdf.name} ({e})")

    # 2. Online Web References
    if REFS.exists():
        for url, given_title in read_refs():
            try:
                text, found_title = fetch(url)
                text = clean(text)
                if len(text.split()) < 150:
                    continue
                sources.append((given_title or found_title or url, url, text))
                print(f"OK   {len(text.split()):>6} words  {url}")
            except Exception as e:
                print(f"SKIP {url} (web fetch timed out or paywalled)")

    if not sources:
        sys.exit("Nothing ingested.")

    chunks = []
    for title, url, text in sources:
        for c in chunk(text):
            chunks.append({"id": len(chunks), "title": title[:200], "url": url, "text": c})

    print(f"\nEmbedding {len(chunks)} chunks from {len(sources)} sources with FastEmbed (batch_size=64)...")
    model = _get()
    raw_vecs = list(model.embed([c["text"] for c in chunks], batch_size=64))
    matrix = _normalize(np.array(raw_vecs, dtype=np.float32))

    DATA_DIR.mkdir(exist_ok=True)
    (DATA_DIR / "chunks.json").write_text(json.dumps(chunks, ensure_ascii=False), encoding="utf-8")
    np.savez_compressed(DATA_DIR / "index.npz", embeddings=matrix.astype(np.float16))
    print(f"\nSUCCESS: Saved {len(chunks)} total chunks from {len(sources)} sources to {DATA_DIR}.")

if __name__ == "__main__":
    main()
