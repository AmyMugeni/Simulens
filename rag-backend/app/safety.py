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
    r"\b(kill(?:ing)? my\s*self|end(?:ing)? my life|tak(?:e|ing) my own life|kms)\b",
    r"\b(want to die|wanna die|wish i was dead|wish i were dead|better off dead)\b",
    r"\b(feel(?:ing|s)? like dying|don'?t want to be here|do not want to be here|want to disappear)\b",
    r"\b(don'?t want to wake up|do not want to wake up|wish i wouldn'?t wake up)\b",
    r"\b(suicide|suicidal|self[- ]harm|hurt(?:ing)? my\s*self|harm(?:ing)? my\s*self|cut(?:ting)? my\s*(?:self|wrists)|hang(?:ing)? my\s*self|overdos(?:e|ing)|swallow(?:ing)? pills|jump(?:ing)? off)\b",
    r"\b(i am going to die|can'?t go on living|cannot go on living)\b",
    r"\b(no reason to live|nothing to live for|not worth living|no point in living|giving up on life|ready to end it)\b",
    r"\b(can'?t take this anymore|cannot take it anymore|tired of everything)\b",
    r"\b(nobody would miss me|no one would miss me|better off without me)\b",
    r"\b(want to end it all|end(?:ing)? it all|tired of living|tired of this life)\b",

    # Kiswahili & Sheng Kenyan conversational expressions
    r"\b(kujitoa maisha|kujiua|nataka kujiua|kujinyonga|kujikata|kujidhuru|nataka kujidhuru)\b",
    r"\b(nataka kufa|natamani kufa|naskia kufa|naskia kukufa|skia kufa|skia kukufa|kukufa|heri nife|afadhali nife|heri nikufe|afadhali nikufe)\b",
    r"\b(sina sababu ya kuishi|hata sioni haja ya kuishi|maisha hayana maana|sioni maana ya kuendelea)\b",
    r"\b(nataka kujiumiza|kujiumiza|kukata roho)\b",
    r"\b(sitaki kuishi tena|nimechoka na maisha|nimechoka kuishi|maisha imenilemea|maisha yanilemee)\b",
    r"\b(sina hope ya kuendelea|nimefika mwisho|nimetoka kwa maisha)\b",
]

# Post-generation safety guard: catch US/foreign crisis numbers if an LLM leaks them
FOREIGN_CRISIS_PATTERNS = [
    r"\b988\b",
    r"\b741\s*741\b",
    r"\b1[- ]?800[- ]?273[- ]?(?:8255|talk)\b",
    r"\bsuicide\s*(?:and|&)\s*crisis\s*lifeline\b",
    r"\bcrisis\s*text\s*line\b",
    r"\btrevor\s*project\b",
    r"\bcall\s*911\b",
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


def contains_foreign_crisis_ref(answer: str) -> bool:
    """Return True if an LLM response leaked US/non-Kenyan crisis hotlines."""
    lowered = unicodedata.normalize("NFKC", answer.lower())
    return any(
        re.search(pattern, lowered)
        for pattern in FOREIGN_CRISIS_PATTERNS
    )
