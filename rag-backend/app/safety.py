"""Crisis screening. Runs BEFORE retrieval or the LLM, so it never depends on a model behaving well."""
import re

# Deliberately broad: a false alarm shows a supportive message, a miss could matter much more.
_CRISIS_PATTERNS = [
    r"\bsuicid",
    r"\bkill (myself|me)\b",
    r"\bend (my|it all|my life)\b",
    r"\bwant(ed)? to die\b",
    r"\bwish i (was|were) (dead|never born)\b",
    r"\bbetter off (dead|without me)\b",
    r"\bno reason to live\b",
    r"\bnot want to (live|be alive|wake up)\b",
    r"\bdon'?t want to (live|be alive|wake up|be here)\b",
    r"\bself[- ]?harm",
    r"\bhurt(ing)? myself\b",
    r"\bcut(ting)? myself\b",
    r"\boverdos",
    r"\bcan'?t go on\b",
    r"\bhopeless\b",
]
_CRISIS_RE = re.compile("|".join(_CRISIS_PATTERNS), re.IGNORECASE)


def is_crisis(text: str) -> bool:
    return bool(_CRISIS_RE.search(text))


# Helplines confirmed against the organisations' own pages / recent listings in Oct 2026.
# Re-check them before you demo or publish: numbers and opening hours change.
CRISIS_MESSAGE = (
    "I'm really sorry you're going through this, and I'm glad you said it out loud. "
    "You deserve support from a real person right now, and I'm not the right tool for this.\n\n"
    "If you might act on these thoughts or are in immediate danger, call 999 or 112 now, "
    "or go to the nearest hospital or ask someone nearby to stay with you.\n\n"
    "You can also reach:\n"
    "- Kenya Red Cross free helpline: 1199 (toll-free)\n"
    "- Befrienders Kenya: +254 722 178 177 (call, SMS or WhatsApp; Monday to Friday, 9am to 5pm)\n\n"
    "If you can, tell a friend, family member, or your campus counselling service how you're feeling today."
)
