package com.wechatmem.app.data.local.search

import java.text.SimpleDateFormat
import java.util.*

/**
 * 时间表达式解析器
 * 解析查询中的时间表达式，如"上周"、"最近"、"去年"等
 */
object TimeParser {

    data class ParseResult(
        val cleanQuery: String,  // 移除时间表达式后的查询
        val startDate: String?,  // 起始日期，null 表示无时间限制
        val timeLabel: String?   // 时间标签，用于显示
    )

    private val timePatterns = listOf(
        Regex("今天") to TimeRange(0, "今天"),
        Regex("昨天") to TimeRange(1, "昨天"),
        Regex("前天") to TimeRange(2, "前天"),
        Regex("上周|最近一周|这周") to TimeRange(7, "最近一周"),
        Regex("最近|近期") to TimeRange(30, "最近一个月"),
        Regex("上个月|上月") to TimeRange(30, "上个月"),
        Regex("最近三个月|三个月") to TimeRange(90, "最近三个月"),
        Regex("最近半年|半年") to TimeRange(180, "最近半年"),
        Regex("去年|上年|最近一年") to TimeRange(365, "最近一年")
    )

    private data class TimeRange(val days: Int, val label: String)

    /**
     * 解析查询中的时间表达式
     * @param query 原始查询
     * @return ParseResult 包含清理后的查询和时间范围
     */
    fun parse(query: String): ParseResult {
        for ((regex, timeRange) in timePatterns) {
            val match = regex.find(query)
            if (match != null) {
                // 移除时间表达式
                val cleanQuery = regex.replace(query, "").trim()

                // 计算起始日期
                val startDate = getDateBefore(timeRange.days)

                return ParseResult(
                    cleanQuery = cleanQuery,
                    startDate = startDate,
                    timeLabel = timeRange.label
                )
            }
        }

        // 没有匹配到时间表达式
        return ParseResult(query, null, null)
    }

    private fun getDateBefore(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            .format(cal.time)
    }
}
