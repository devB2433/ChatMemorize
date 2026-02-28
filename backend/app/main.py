from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded

from app.config import settings
from app.database import init_db
from app.api.health import router as health_router
from app.api.conversations import router as conversations_router
from app.api.search import router as search_router
from app.api.auth import router as auth_router
from app.limiter import limiter
from app.logging_config import setup_logging
from app.middleware.logging import RequestLoggingMiddleware


@asynccontextmanager
async def lifespan(app: FastAPI):
    setup_logging(debug=settings.debug)
    await init_db()
    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan)

app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

app.add_middleware(RequestLoggingMiddleware)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health_router, prefix="/api/v1")
app.include_router(auth_router, prefix="/api/v1")
app.include_router(conversations_router, prefix="/api/v1")
app.include_router(search_router, prefix="/api/v1")
