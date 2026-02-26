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

    model_config = {"env_prefix": "WECHATMEM_", "env_file": ".env"}

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.data_dir.mkdir(parents=True, exist_ok=True)
        if not self.sqlite_url:
            self.sqlite_url = f"sqlite+aiosqlite:///{self.data_dir / 'wechatmem.db'}"
        if not self.chroma_dir:
            self.chroma_dir = str(self.data_dir / "chroma")


settings = Settings()
