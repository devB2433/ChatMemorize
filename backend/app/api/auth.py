import uuid
from datetime import datetime, timezone

import sqlalchemy as sa
from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.user import User
from app.schemas.auth import RegisterRequest, LoginRequest, TokenResponse, UserOut
from app.services.auth import hash_password, verify_password, create_token, get_current_user
from app.limiter import limiter
from app.config import settings

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/refresh", response_model=TokenResponse)
@limiter.limit(settings.rate_limit_auth)
async def refresh_token(request: Request, current_user: User = Depends(get_current_user)):
    """Issue a new token if the current one is still valid."""
    return TokenResponse(token=create_token(current_user.id))


@router.post("/register", response_model=TokenResponse, status_code=201)
@limiter.limit(settings.rate_limit_auth)
async def register(request: Request, body: RegisterRequest, db: AsyncSession = Depends(get_db)):
    exists = (
        await db.execute(sa.select(User).where(User.username == body.username))
    ).scalar_one_or_none()
    if exists:
        raise HTTPException(status.HTTP_409_CONFLICT, "用户名已存在")

    user = User(
        id=str(uuid.uuid4()),
        username=body.username,
        password=hash_password(body.password),
        created_at=datetime.now(timezone.utc).isoformat(),
    )
    db.add(user)
    await db.commit()
    return TokenResponse(token=create_token(user.id))


@router.post("/login", response_model=TokenResponse)
@limiter.limit(settings.rate_limit_auth)
async def login(request: Request, body: LoginRequest, db: AsyncSession = Depends(get_db)):
    user = (
        await db.execute(sa.select(User).where(User.username == body.username))
    ).scalar_one_or_none()
    if not user or not verify_password(body.password, user.password):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "用户名或密码错误")
    return TokenResponse(token=create_token(user.id))


@router.get("/me", response_model=UserOut)
@limiter.limit(settings.rate_limit_api)
async def me(request: Request, current_user: User = Depends(get_current_user)):
    return UserOut(id=current_user.id, username=current_user.username, created_at=current_user.created_at)
