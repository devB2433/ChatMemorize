import logging
import json
import sys
from datetime import datetime, timezone


class JSONFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        log_obj = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        if record.exc_info and record.exc_info[0]:
            log_obj["exception"] = self.formatException(record.exc_info)
        if hasattr(record, "request_id"):
            log_obj["request_id"] = record.request_id
        return json.dumps(log_obj, ensure_ascii=False)


def setup_logging(debug: bool = False) -> None:
    level = logging.DEBUG if debug else logging.INFO
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JSONFormatter())

    root = logging.getLogger()
    root.setLevel(level)
    root.handlers.clear()
    root.addHandler(handler)

    # Quiet noisy libs
    for name in ("uvicorn.access", "httpcore", "httpx", "chromadb"):
        logging.getLogger(name).setLevel(logging.WARNING)
