import re
import unicodedata

CRISIS_MESSAGE = (
    "I'm so sorry you're going through such a difficult time. "
    "Your life and safety matter, and you do not have to carry this alone.\n\n"
    "Please reach out for immediate, confidential support in Kenya right now:\n"
    "• Befrienders Kenya Helpline: +254 722 178 177 (24/7 Suicide Prevention & Support)\n"
    "• Kenya Red Cross Toll-Free Helpline: 1199\n"
    "• Kenya Emergency Services: Call 999 or 112 (or visit your nearest hospital emergency department)\n\n"
    "Please talk to someone you trust — a friend, relative, or counselor. Help is available, and you matter."
)

# Expressions that indicate suicidal thoughts, self-harm,
# or an immediate risk of harm in English, Swahili, and Sheng.
CRISIS_PATTERNS = [
    # English expressions & variations
    r"\b(kill my\s*self|end my life|take my own life)\b",
    r"\b(want to die|wish i was dead|wish i were dead|better off dead)\b",
    r"\b(feel like dying|feeling like dying|feels like dying)\b",
    r"\b(dont want to wake up|don't want to wake up|wish i wouldnt wake up|wish i wouldn't wake up)\b",
    r"\b(suicide|suicidal|self[- ]harm|hurt my\s*self|cutting my\s*wrists|overdose)\b",
    r"\b(i am going to die|i cannot go on living|cant go on living|can't go on living)\b",
    r"\b(no reason to live|nothing to live for|giving up on life|ready to end it)\b",
    r"\b(cant take this anymore|can't take this anymore|cannot take it anymore|tired of everything)\b",
    r"\b(nobody would miss me|no one would miss me|better off without me)\b",
    r"\b(i want to end it all|i am tired of living|tired of this life)\b",

    # Kiswahili & Sheng Kenyan conversational expressions
    r"\b(kujitoa maisha|kujiua|nataka kujiua|kujinyonga|kujikata)\b",
    r"\b(nataka kufa|natamani kufa|heri nife|afadhali nife|heri nikufe|afadhali nikufe)\b",
    r"\b(sina sababu ya kuishi|hata sioni haja ya kuishi|maisha hayana maana)\b",
    r"\b(nataka kujiumiza|kujiumiza|kukata roho)\b",
    r"\b(sitaki kuishi tena|nimechoka na maisha|nimechoka kuishi|maisha imenilemea|maisha yanilemee)\b",
    r"\b(sina hope ya kuendelea|nimefika mwisho|nimetoka kwa maisha)\b",
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
