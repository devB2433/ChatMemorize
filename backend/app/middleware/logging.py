import logging
import time
import uuid

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

logger = logging.getLogger("wechatmem.access")


class RequestLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        request_id = str(uuid.uuid4())[:8]
        request.state.request_id = request_id

        start = time.perf_counter()
        response: Response = await call_next(request)
        duration_ms = round((time.perf_counter() - start) * 1000, 1)

        logger.info(
            "%s %s -> %s (%.1fms) [%s]",
            request.method,
            request.url.path,
            response.status_code,
            duration_ms,
            request_id,
            extra={"request_id": request_id},
        )
        response.headers["X-Request-ID"] = request_id
        return response
