from app.services.parser import parse_wechat_text


def test_parse_with_timestamps():
    text = """张三 2024-01-15 10:30
你好啊

李四 2024-01-15 10:31
你好！最近怎么样？

张三 2024-01-15 10:32
挺好的，谢谢"""
    result = parse_wechat_text(text)
    assert len(result.messages) == 3
    assert result.participants == ["张三", "李四"]
    assert result.messages[0].sender == "张三"
    assert result.messages[0].content == "你好啊"
    assert result.messages[0].timestamp == "2024-01-15 10:30"
    assert result.messages[1].sender == "李四"
    assert result.messages[2].sequence == 2


def test_parse_colon_format():
    text = """张三：
你好

李四：
你好！"""
    result = parse_wechat_text(text)
    assert len(result.messages) == 2
    assert result.messages[0].sender == "张三"
    assert result.messages[0].timestamp is None


def test_parse_multiline_content():
    text = """张三 2024-01-15 10:30
第一行
第二行
第三行

李四 2024-01-15 10:31
回复"""
    result = parse_wechat_text(text)
    assert result.messages[0].content == "第一行\n第二行\n第三行"


def test_parse_empty_text():
    result = parse_wechat_text("")
    assert len(result.messages) == 0
    assert result.participants == []


def test_parse_image_placeholders():
    text = """张三 2024-01-15 10:30
你好啊

李四 2024-01-15 10:31
[图片]

张三 2024-01-15 10:32
[图片]

李四 2024-01-15 10:33
好的"""
    result = parse_wechat_text(text)
    assert len(result.messages) == 4
    assert result.image_count == 2
    # First image
    assert result.messages[1].msg_type == "image"
    assert result.messages[1].image_index == 0
    # Second image
    assert result.messages[2].msg_type == "image"
    assert result.messages[2].image_index == 1
    # Text messages
    assert result.messages[0].msg_type == "text"
    assert result.messages[3].msg_type == "text"


def test_parse_media_placeholders():
    text = """张三 2024-01-15 10:30
[语音]

李四 2024-01-15 10:31
[视频]"""
    result = parse_wechat_text(text)
    assert result.messages[0].msg_type == "media"
    assert result.messages[1].msg_type == "media"
    assert result.image_count == 0


def test_parse_wechat_export_format():
    """WeChat export format: date separator + sender  HH:MM + image with filename."""
    text = """Smarttang 和 Songtao 在微信上的聊天记录如下，请查收。
—————  2026-02-26  —————
BL 唐誉聪  08:54
[图片: 8b0abeb1ccf49ed225b85b4c96a35d98.png.jpg(请在附件中查看)]
BL 唐誉聪  08:54
blingsec大模型存储压缩工具
Songtao  08:56
等我两分钟啊 我倒个垃圾
BL 唐誉聪  08:56
OK"""
    result = parse_wechat_text(text)
    assert result.participants == ["BL 唐誉聪", "Songtao"]
    assert len(result.messages) == 4
    assert result.image_count == 1
    # Image message
    assert result.messages[0].msg_type == "image"
    assert result.messages[0].image_index == 0
    assert result.messages[0].timestamp == "2026-02-26 08:54"
    # Text messages
    assert result.messages[1].content == "blingsec大模型存储压缩工具"
    assert result.messages[2].sender == "Songtao"
    assert result.messages[3].content == "OK"
