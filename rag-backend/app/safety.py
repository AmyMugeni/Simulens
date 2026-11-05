import re
import unicodedata

CRISIS_MESSAGE = (
    "I'm sorry you're going through a difficult time. "
    "Your safety matters, and you deserve support. "
    "If you are in immediate danger or feel you may hurt yourself, "
    "please go to the nearest hospital emergency department or call "
    "Kenya's emergency services on 999 or 112. "
    "You can also reach out to someone you trust and ask them to stay "
    "with you. You do not have to handle this alone."
)

# Expressions that may indicate suicidal thoughts, self-harm,
# or an immediate risk of harm.
CRISIS_PATTERNS = [
    # English expressions
    r"\b(kill myself|end my life|take my own life)\b",
    r"\b(want to die|wish i was dead|better off dead)\b",
    r"\b(suicide|suicidal|self[- ]harm|hurt myself)\b",
    r"\b(i am going to die|i cannot go on living)\b",
    r"\b(no reason to live|nothing to live for)\b",

    # Kiswahili expressions
    r"\b(kujitoa maisha|kujiua|nataka kujiua)\b",
    r"\b(nataka kufa|natamani kufa)\b",
    r"\b(sina sababu ya kuishi)\b",
    r"\b(nataka kujiumiza|kujiumiza)\b",
    r"\b(maisha hayana maana)\b",

    # Common Kenyan conversational expressions
    r"\b(i want to end it all)\b",
    r"\b(i am tired of living)\b",
    r"\b(heri nife|afadhali nife)\b",
    r"\b(sina hope ya kuendelea)\b",
]


def normalize_text(text: str) -> str:
    """Normalize text before checking crisis-related expressions."""
    text = unicodedata.normalize("NFKC", text.lower())
    text = re.sub(r"[^\w\s'-]", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def is_crisis(message: str) -> bool:
    """Return True when a message contains a recognised crisis expression."""
    normalized = normalize_text(message)

    return any(
        re.search(pattern, normalized)
        for pattern in CRISIS_PATTERNS
    )
