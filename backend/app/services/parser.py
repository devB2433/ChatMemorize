"""WeChat chat text parser.

Handles the common format when sharing chat records from WeChat:
  张三 2024-01-15 10:30
  你好啊

  李四 2024-01-15 10:31
  你好！最近怎么样？

Also handles the WeChat export format:
  —————  2026-02-26  —————
  BL 唐誉聪  08:54
  [图片: abc.jpg(请在附件中查看)]
  Songtao  08:56
  你好

Also handles variants without timestamps, and group chat formats.
Recognizes [图片], [语音], [视频] etc. as media placeholders.
"""
import re
from dataclasses import dataclass, field

# Matches: sender YYYY-MM-DD HH:MM(:SS)
_HEADER_WITH_TS = re.compile(
    r"^(.+?)\s+(\d{4}[-/]\d{1,2}[-/]\d{1,2}\s+\d{1,2}:\d{2}(?::\d{2})?)\s*$"
)
# Matches: sender  HH:MM (time only, WeChat export format)
_HEADER_TIME_ONLY = re.compile(
    r"^(.+?)\s{2,}(\d{1,2}:\d{2}(?::\d{2})?)\s*$"
)
# Matches: —————  2026-02-26  ————— (date separator line)
_DATE_SEPARATOR = re.compile(
    r"^[—\-─]+\s*(\d{4}[-/]\d{1,2}[-/]\d{1,2})\s*[—\-─]+$"
)
# Matches: xxx在微信上的聊天记录如下 (description line to skip)
_DESCRIPTION_LINE = re.compile(r"微信上的聊天记录如下")
# Matches: sender:  (colon-separated, no timestamp)
_HEADER_COLON = re.compile(r"^(.+?)[:：]\s*$")

# Media placeholder pattern: [图片], [图片1], [Photo], [图片: xxx.jpg(请在附件中查看)] etc.
_IMAGE_PLACEHOLDER = re.compile(
    r"\[图片\d*\]|\[Photo\d*\]|\[IMAGE\d*\]|\[图片:\s*.+?\]",
    re.IGNORECASE,
)
_MEDIA_PLACEHOLDER = re.compile(
    r"\[(图片|语音|视频|动画表情|文件|链接|名片|位置|转账|红包|应用消息|Photo|Video|Voice|Sticker|App)"
    r"(?:\d*|:\s*.+?)\]",
    re.IGNORECASE,
)


@dataclass
class ParsedMessage:
    sender: str
    content: str
    timestamp: str | None = None
    sequence: int = 0
    msg_type: str = "text"  # "text", "image", "media"
    image_index: int | None = None  # maps to the N-th image in the upload


@dataclass
class ParsedConversation:
    participants: list[str] = field(default_factory=list)
    messages: list[ParsedMessage] = field(default_factory=list)
    image_count: int = 0  # total [图片] placeholders found


def parse_wechat_text(text: str) -> ParsedConversation:
    """Parse WeChat shared text into structured messages."""
    lines = text.strip().splitlines()
    messages: list[ParsedMessage] = []
    current_sender: str | None = None
    current_ts: str | None = None
    content_lines: list[str] = []
    current_date: str | None = None  # date context from separator
    seq = 0
    image_idx = 0  # running counter for image placeholders

    def flush():
        nonlocal seq, image_idx
        if current_sender:
            content = "\n".join(content_lines).strip() if content_lines else ""
            if not content:
                content = "[内容未导出]"
            if content:
                content_bare = content.strip()
                if _IMAGE_PLACEHOLDER.fullmatch(content_bare):
                    msg = ParsedMessage(
                        sender=current_sender,
                        content=content,
                        timestamp=current_ts,
                        sequence=seq,
                        msg_type="image",
                        image_index=image_idx,
                    )
                    image_idx += 1
                elif _MEDIA_PLACEHOLDER.fullmatch(content_bare):
                    msg = ParsedMessage(
                        sender=current_sender,
                        content=content,
                        timestamp=current_ts,
                        sequence=seq,
                        msg_type="media",
                    )
                else:
                    msg = ParsedMessage(
                        sender=current_sender,
                        content=content,
                        timestamp=current_ts,
                        sequence=seq,
                    )
                messages.append(msg)
                seq += 1

    for line in lines:
        line_stripped = line.strip()
        if not line_stripped:
            continue

        # Skip description line
        if _DESCRIPTION_LINE.search(line_stripped):
            continue

        # Date separator: —————  2026-02-26  —————
        m = _DATE_SEPARATOR.match(line_stripped)
        if m:
            current_date = m.group(1).strip()
            continue

        # Header with full timestamp: sender YYYY-MM-DD HH:MM
        m = _HEADER_WITH_TS.match(line_stripped)
        if m:
            flush()
            current_sender = m.group(1).strip()
            current_ts = m.group(2).strip()
            content_lines = []
            continue

        # Header with time only: sender  HH:MM (WeChat export)
        m = _HEADER_TIME_ONLY.match(line_stripped)
        if m:
            flush()
            current_sender = m.group(1).strip()
            time_part = m.group(2).strip()
            current_ts = f"{current_date} {time_part}" if current_date else time_part
            content_lines = []
            continue

        # Colon-separated header
        m = _HEADER_COLON.match(line_stripped)
        if m and len(m.group(1)) <= 30:
            flush()
            current_sender = m.group(1).strip()
            current_ts = None
            content_lines = []
            continue

        # Content line
        if current_sender is not None:
            content_lines.append(line_stripped)
        else:
            current_sender = "Unknown"
            current_ts = None
            content_lines.append(line_stripped)

    flush()

    participants = list(dict.fromkeys(msg.sender for msg in messages))

    return ParsedConversation(
        participants=participants, messages=messages, image_count=image_idx
    )
