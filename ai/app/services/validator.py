"""Minimal cleanup for raw LLM responses before validation."""


def extract_json_body(response_text: str) -> str:
    """Remove an outer code fence and keep the outermost JSON object text."""
    cleaned = _remove_outer_code_fence(response_text)
    opening_brace = cleaned.find("{")
    closing_brace = cleaned.rfind("}")

    if opening_brace == -1:
        return cleaned
    if closing_brace < opening_brace:
        return cleaned[opening_brace:]
    return cleaned[opening_brace : closing_brace + 1]


def _remove_outer_code_fence(response_text: str) -> str:
    stripped = response_text.strip()
    if not stripped.startswith("```"):
        return response_text

    first_newline = stripped.find("\n")
    if first_newline == -1:
        return ""

    fenced_body = stripped[first_newline + 1 :]
    if fenced_body.rstrip().endswith("```"):
        return fenced_body.rstrip()[:-3].rstrip()
    return fenced_body
