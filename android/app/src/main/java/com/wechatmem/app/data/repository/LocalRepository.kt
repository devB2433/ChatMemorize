package com.wechatmem.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.wechatmem.app.data.local.AppPrefs
import com.wechatmem.app.data.local.db.*
import com.wechatmem.app.data.local.embedding.EmbeddingModel
import com.wechatmem.app.data.local.embedding.ModelDownloader
import com.wechatmem.app.data.local.search.LocalVectorSearch
import com.wechatmem.app.data.model.*
import com.wechatmem.app.data.remote.LlmClient
import com.wechatmem.app.parser.WeChatTextParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LocalRepository(private val context: Context) : ConversationRepository {

    private val db get() = AppDatabase.getInstance(context)
    private val gson = Gson()
    private val vectorSearch get() = LocalVectorSearch(db.vectorDao())

    private val WORK_KEYWORDS = setOf("工作", "会议", "项目", "需求", "任务", "开发", "测试", "上班", "客户", "报告", "方案", "汇报", "进度", "产品", "运营", "deadline", "bug")
    private val LIFE_KEYWORDS = setOf("吃饭", "旅游", "朋友", "周末", "假期", "购物", "电影", "聚餐", "运动", "旅行", "美食", "生日", "结婚", "孩子", "家人")

    private var embeddingModel: EmbeddingModel? = null

    private suspend fun getOrCreateEmbedding(): EmbeddingModel {
        if (embeddingModel == null) {
            ModelDownloader.ensureReady(context)
            embeddingModel = EmbeddingModel(
                ModelDownloader.modelFile(context),
                ModelDownloader.vocabFile(context)
            )
        }
        return embeddingModel!!
    }

    private fun getLlm(): LlmClient {
        val key = AppPrefs.getZhipuApiKey(context)
        if (key.isBlank()) throw Exception("请先在设置中配置智谱 API Key")
        return LlmClient(key, AppPrefs.getLlmModel(context))
    }

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

    override suspend fun getConversations(page: Int, pageSize: Int): PaginatedConversations {
        val total = db.conversationDao().count()
        val offset = (page - 1) * pageSize
        val items = db.conversationDao().getPage(pageSize, offset).map { entity ->
            val tags = db.tagDao().getTagsForConversation(entity.id).map { TagInfo(it.id, it.name, it.colorHex) }
            entity.toBrief(tags)
        }
        return PaginatedConversations(items, total, page, pageSize)
    }

    override suspend fun getConversation(id: String): ConversationDetail {
        val conv = db.conversationDao().getById(id) ?: throw Exception("对话不存在")
        val msgs = db.messageDao().getByConversation(id).map {
            MessageOut(it.id, it.sender, it.content, it.timestamp, it.sequence)
        }
        return ConversationDetail(
            conv.id, conv.title, parseParticipants(conv.participants),
            conv.messageCount, conv.summary, conv.createdAt, conv.updatedAt, msgs
        )
    }

    override suspend fun createConversation(text: String, title: String?): ConversationBrief {
        val parsed = WeChatTextParser.parse(text)
        val convId = UUID.randomUUID().toString()
        val ts = now()
        val generatedTitle = title ?: generateTitle(parsed)

        val entity = ConversationEntity(
            id = convId, title = generatedTitle,
            participants = gson.toJson(parsed.participants),
            messageCount = parsed.messages.size,
            createdAt = ts, updatedAt = ts
        )
        db.conversationDao().insert(entity)

        val msgEntities = parsed.messages.map { m ->
            MessageEntity(UUID.randomUUID().toString(), convId, m.sender, m.content, m.timestamp, m.sequence)
        }
        db.messageDao().insertAll(msgEntities)

        // Embed asynchronously if model ready
        try {
            val model = getOrCreateEmbedding()
            val vectors = msgEntities.map { m ->
                VectorEntity(
                    m.id, convId,
                    LocalVectorSearch.floatsToBytes(model.encode(m.content))
                )
            }
            db.vectorDao().insertAll(vectors)
        } catch (_: Exception) { /* model not ready, skip embedding */ }

        // Generate summary + auto-tag in background
        CoroutineScope(Dispatchers.IO).launch {
            try { generateSummary(convId) } catch (_: Exception) {}
            try { autoTag(convId, msgEntities) } catch (_: Exception) {}
        }

        return entity.toBrief()
    }

    override suspend fun deleteConversation(id: String) {
        db.conversationDao().deleteById(id) // CASCADE deletes messages + vectors
    }

    override suspend fun search(query: String, topK: Int): SearchResponse {
        val model = getOrCreateEmbedding()
        val queryVec = model.encode(query)

        // 使用时间范围阶梯搜索
        val (results, metadata) = vectorSearch.searchWithTimeRange(queryVec, topK * 2, minResults = 5)

        // 同时搜索摘要
        val summaryResults = searchInSummaries(queryVec, topK)

        // 合并结果，按分数排序，去重
        val allResults = (results + summaryResults)
            .groupBy { it.conversationId }
            .map { (_, group) -> group.maxByOrNull { it.score }!! }
            .sortedByDescending { it.score }
            .take(topK)

        val searchResults = allResults.mapNotNull { r ->
            // 如果是摘要结果，messageId 以 "summary_" 开头
            if (r.messageId.startsWith("summary_")) {
                val conv = db.conversationDao().getById(r.conversationId) ?: return@mapNotNull null
                SearchResult(
                    r.messageId,
                    r.conversationId,
                    "摘要",
                    conv.summary ?: "",
                    conv.updatedAt,
                    r.score
                )
            } else {
                val msg = db.messageDao().getById(r.messageId) ?: return@mapNotNull null
                SearchResult(msg.id, msg.conversationId, msg.sender, msg.content, msg.timestamp, r.score)
            }
        }

        android.util.Log.d("LocalRepository", "Search: ${metadata.timeRange}, found ${searchResults.size} results")
        return SearchResponse(searchResults, query)
    }

    /**
     * 在摘要中搜索
     */
    private suspend fun searchInSummaries(queryVec: FloatArray, topK: Int): List<LocalVectorSearch.Result> {
        // 获取所有有摘要的对话
        val conversations = db.conversationDao().getAll().filter { !it.summary.isNullOrBlank() }

        val model = getOrCreateEmbedding()
        return conversations.mapNotNull { conv ->
            try {
                val summaryVec = model.encode(conv.summary!!)
                val score = cosine(queryVec, summaryVec)
                LocalVectorSearch.Result("summary_${conv.id}", conv.id, score)
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.score }.take(topK)
    }

    /**
     * 计算余弦相似度
     */
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(na) * kotlin.math.sqrt(nb)
        return if (denom > 0) dot / denom else 0f
    }

    override suspend fun ask(question: String, topK: Int): AskResponse {
        val searchResp = search(question, topK)
        val answer = try {
            val contextText = searchResp.results.joinToString("\n") { "${it.sender}: ${it.content}" }
            getLlm().ask(question, contextText)
        } catch (_: Exception) {
            "（无网络或未配置 API Key，仅显示搜索结果）"
        }
        return AskResponse(answer, searchResp.results)
    }

    override suspend fun generateSummary(id: String): String {
        val conv = db.conversationDao().getById(id) ?: throw Exception("对话不存在")
        val msgs = db.messageDao().getByConversation(id)
        val text = msgs.joinToString("\n") { "${it.sender}: ${it.content}" }
        val summary = getLlm().summarize(text)
        db.conversationDao().updateSummary(id, summary, now())
        return summary
    }

    override suspend fun getConversationsByTag(tagId: String): List<ConversationBrief> {
        val convIds = db.tagDao().getConversationIdsByTag(tagId)
        return convIds.mapNotNull { id ->
            db.conversationDao().getById(id)?.let { entity ->
                val tags = db.tagDao().getTagsForConversation(id).map { TagInfo(it.id, it.name, it.colorHex) }
                entity.toBrief(tags)
            }
        }.sortedByDescending { it.updatedAt }
    }

    private fun generateTitle(parsed: ParsedConversation): String {
        val p = parsed.participants
        val participantStr = when {
            p.isEmpty() -> "未知对话"
            p.size == 1 -> p[0]
            p.size == 2 -> "${p[0]}·${p[1]}"
            else -> "${p[0]}等${p.size}人"
        }
        val firstTs = parsed.messages.firstOrNull()?.timestamp
        val dateStr = firstTs?.let { ts ->
            Regex("(\\d{1,2})[月/\\-](\\d{1,2})").find(ts)?.let { m ->
                "${m.groupValues[1]}月${m.groupValues[2]}日"
            }
        } ?: ""
        return if (dateStr.isNotBlank()) "$participantStr | $dateStr" else participantStr
    }

    private suspend fun autoTag(convId: String, messages: List<MessageEntity>) {
        val allText = messages.joinToString(" ") { it.content }
        val tagDao = db.tagDao()
        if (WORK_KEYWORDS.any { allText.contains(it) }) {
            tagDao.insert(TagEntity("tag_work", "工作", "#1976D2"))
            tagDao.insertConversationTag(ConversationTagEntity(convId, "tag_work"))
        }
        if (LIFE_KEYWORDS.any { allText.contains(it) }) {
            tagDao.insert(TagEntity("tag_life", "生活", "#43A047"))
            tagDao.insertConversationTag(ConversationTagEntity(convId, "tag_life"))
        }
    }

    private fun ConversationEntity.toBrief(tags: List<TagInfo> = emptyList()) = ConversationBrief(
        id, title, parseParticipants(participants), messageCount, summary, createdAt, updatedAt, tags
    )

    private fun parseParticipants(json: String): List<String> =
        try { gson.fromJson(json, Array<String>::class.java).toList() } catch (_: Exception) { emptyList() }
}
