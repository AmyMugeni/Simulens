# CogniLens RAG backend

FastAPI service that answers questions about social media and wellbeing using only your project's references.
Free stack: BGE-small embeddings (runs on the server CPU), Groq free tier for the LLM, Render free web service.

## 1. Build the knowledge base (on your laptop, once)
1. Put your reference links in `references.txt` (one per line, optional `URL | Title`).
   Paywalled papers can't be fetched: download the PDF and drop it in a `pdfs/` folder.
2. Run:
   ```
   python -m venv .venv && source .venv/bin/activate      # Windows: .venv\Scripts\activate
   pip install -r requirements-ingest.txt
   python -m scripts.ingest
   ```
   Read the OK / SKIP / FAIL lines. A source that says SKIP gave too little text. Fix or replace it.
3. This creates `data/chunks.json` and `data/index.npz`. Commit the `data/` folder.

## 2. Get a free LLM key
Sign up at console.groq.com (no card needed), create an API key, and check the current model name at
console.groq.com/docs/models. Put it in `LLM_MODEL` if `llama-3.3-70b-versatile` has been retired.

## 3. Run locally
```
cp .env.example .env        # fill in LLM_API_KEY
export $(grep -v '^#' .env | xargs)
uvicorn app.main:app --reload
curl -X POST localhost:8000/chat -H "X-App-Token: change-me" -H "Content-Type: application/json" \
     -d '{"message":"Does scrolling before bed affect sleep?"}'
```

## 4. Deploy on Render (free)
Push this folder to GitHub (with `data/`), then Render > New > Blueprint > pick the repo.
Set `LLM_API_KEY` in the dashboard. Render generates `APP_TOKEN`: copy it into the Android build.
Free instances sleep after ~15 minutes idle, so the first request takes up to a minute (the app says so).
The 512 MB RAM limit is enough for the small embedding model; if the build runs out of memory, tell me.

## Android
Add the three files in `android/`, then in `local.properties`:
```
RAG_BASE_URL=https://cognilens-rag.onrender.com
RAG_APP_TOKEN=the_token_from_render
```
and in `app/build.gradle.kts` (next to AI_API_KEY): `buildConfigField("String", "RAG_BASE_URL", "\"${localProps.getProperty("RAG_BASE_URL", "")}\"")`
and the same for `RAG_APP_TOKEN`.

## Notes
- The app token is a shared secret that can be pulled out of the APK. It stops casual abuse, not a determined person.
  Per-IP rate limits and Groq's own limits cap the damage. Fine for a project, not for production.
- Message text is never logged by the server.
- Crisis messages are matched by keywords before any AI call, and the helplines are in `app/safety.py`. Re-verify them before a demo.
