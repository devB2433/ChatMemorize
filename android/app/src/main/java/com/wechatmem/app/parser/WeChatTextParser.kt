package com.wechatmem.app.parser

import com.wechatmem.app.data.model.ParsedConversation
import com.wechatmem.app.data.model.ParsedMessage

/**
 * Mirrors the backend parser logic in parser.py.
 * Parses WeChat shared text into structured messages.
 *
 * Supported formats:
 *   张三 2024-01-15 10:30
 *   你好啊
 *
 *   —————  2026-02-26  —————
 *   BL 唐誉聪  08:54
 *   [图片: abc.jpg(请在附件中查看)]
 *
 *   李四：
 *   你好！
 */
object WeChatTextParser {

    // Matches: sender YYYY-MM-DD HH:MM(:SS)
    private val HEADER_WITH_TS = Regex(
        """^(.+?)\s+(\d{4}[-/]\d{1,2}[-/]\d{1,2}\s+\d{1,2}:\d{2}(?::\d{2})?)\s*$"""
    )

    // Matches: sender  HH:MM (time only, 2+ spaces before time)
    private val HEADER_TIME_ONLY = Regex(
        """^(.+?)\s{2,}(\d{1,2}:\d{2}(?::\d{2})?)\s*$"""
    )

    // Matches: —————  2026-02-26  ————— (date separator line)
    private val DATE_SEPARATOR = Regex(
        """^[—\-─]+\s*(\d{4}[-/]\d{1,2}[-/]\d{1,2})\s*[—\-─]+$"""
    )

    // Matches: xxx在微信上的聊天记录如下
    private val DESCRIPTION_LINE = Regex("""在微信上的聊天记录如下""")

    // Matches: sender: or sender：  (colon-separated, no timestamp)
    private val HEADER_COLON = Regex("""^(.+?)[:\uff1a]\s*$""")

    fun parse(text: String): ParsedConversation {
        val lines = text.trim().lines()
        val messages = mutableListOf<ParsedMessage>()
        var currentSender: String? = null
        var currentTs: String? = null
        var currentDate: String? = null
        val contentLines = mutableListOf<String>()
        var seq = 0

        fun flush() {
            val sender = currentSender ?: return
            val content = contentLines.joinToString("\n").trim()
            if (content.isNotEmpty()) {
                messages.add(
                    ParsedMessage(
                        sender = sender,
                        content = content,
                        timestamp = currentTs,
                        sequence = seq
                    )
                )
                seq++
            }
        }

        for (line in lines) {
            val stripped = line.trim()
            if (stripped.isEmpty()) continue

            // Skip description line
            if (DESCRIPTION_LINE.containsMatchIn(stripped)) continue

            // Date separator: —————  2026-02-26  —————
            val dateMatch = DATE_SEPARATOR.matchEntire(stripped)
            if (dateMatch != null) {
                currentDate = dateMatch.groupValues[1].trim()
                continue
            }

            // Try header with full timestamp
            val tsMatch = HEADER_WITH_TS.matchEntire(stripped)
            if (tsMatch != null) {
                flush()
                currentSender = tsMatch.groupValues[1].trim()
                currentTs = tsMatch.groupValues[2].trim()
                contentLines.clear()
                continue
            }

            // Try header with time only (2+ spaces before time)
            val timeMatch = HEADER_TIME_ONLY.matchEntire(stripped)
            if (timeMatch != null) {
                flush()
                currentSender = timeMatch.groupValues[1].trim()
                val timePart = timeMatch.groupValues[2].trim()
                currentTs = if (currentDate != null) "$currentDate $timePart" else timePart
                contentLines.clear()
                continue
            }

            // Try colon-separated header (only if short enough to be a name)
            val colonMatch = HEADER_COLON.matchEntire(stripped)
            if (colonMatch != null && colonMatch.groupValues[1].length <= 30) {
                flush()
                currentSender = colonMatch.groupValues[1].trim()
                currentTs = null
                contentLines.clear()
                continue
            }

            // Content line
            if (currentSender != null) {
                contentLines.add(stripped)
            } else {
                // Text before any header - treat as first message from "Unknown"
                currentSender = "Unknown"
                currentTs = null
                contentLines.add(stripped)
            }
        }

        flush()

        // Extract unique participants preserving order
        val participants = messages.map { it.sender }.distinct()

        return ParsedConversation(
            participants = participants,
            messages = messages
        )
    }
}
