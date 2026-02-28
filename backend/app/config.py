from pydantic_settings import BaseSettings
from pathlib import Path


class Settings(BaseSettings):
    app_name: str = "WeChatMem"
    debug: bool = False

    # paths
    data_dir: Path = Path(__file__).resolve().parent.parent / "data"
    sqlite_url: str = ""
    chroma_dir: str = ""

    # embedding
    embedding_model: str = "BAAI/bge-small-zh-v1.5"

    # zhipu glm
    zhipu_api_key: str = ""
    zhipu_model: str = "glm-4-flash"

    # jwt
    jwt_secret: str = "wechatmem-dev-secret-change-in-production"
    jwt_expire_minutes: int = 60 * 24 * 7  # 7 days

    # pagination
    default_page_size: int = 20
    max_page_size: int = 100

    # CORS
    cors_origins: str = "*"

    # rate limiting (slowapi format)
    rate_limit_auth: str = "10/minute"
    rate_limit_api: str = "60/minute"

    # upload limits
    max_image_size_mb: int = 10
    max_upload_total_mb: int = 50
    max_title_length: int = 200

    model_config = {"env_prefix": "WECHATMEM_", "env_file": ".env"}

    @property
    def cors_origins_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.data_dir.mkdir(parents=True, exist_ok=True)
        if not self.sqlite_url:
            self.sqlite_url = f"sqlite+aiosqlite:///{self.data_dir / 'wechatmem.db'}"
        if not self.chroma_dir:
            self.chroma_dir = str(self.data_dir / "chroma")


settings = Settings()
