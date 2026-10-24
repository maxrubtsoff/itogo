import hmac
import os

from fastapi import Header, HTTPException, status

API_TOKEN = os.environ.get("API_TOKEN", "")


def require_token(authorization: str = Header(default="")) -> None:
    """Validates the `Authorization: Bearer <token>` header in constant time."""
    if not API_TOKEN:
        raise HTTPException(status.HTTP_500_INTERNAL_SERVER_ERROR, "API_TOKEN not configured")
    prefix = "Bearer "
    token = authorization[len(prefix):] if authorization.startswith(prefix) else ""
    if not token or not hmac.compare_digest(token, API_TOKEN):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid token")
