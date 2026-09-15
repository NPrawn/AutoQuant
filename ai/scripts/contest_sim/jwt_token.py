"""HS256 access token in the shape backend JwtTokenProvider accepts. Local contest runs only."""

import base64
import hashlib
import hmac
import json
import time


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def issue(member_id: str, secret: str, issuer: str = "a107", ttl_seconds: int = 12 * 3600,
          now: int | None = None) -> str:
    if len(secret.encode("utf-8")) < 32:
        raise ValueError("JWT secret must be at least 32 bytes, like backend JWT_SECRET")
    issued = int(time.time()) if now is None else now
    header = _b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode("utf-8"))
    claims = {"iss": issuer, "sub": member_id, "iat": issued, "exp": issued + ttl_seconds}
    payload = _b64(json.dumps(claims, separators=(",", ":")).encode("utf-8"))
    signature = hmac.new(secret.encode("utf-8"), f"{header}.{payload}".encode("ascii"), hashlib.sha256).digest()
    return f"{header}.{payload}.{_b64(signature)}"
