package com.wechatmem.app.data.local.search

import android.util.Log
import com.wechatmem.app.data.local.db.VectorDao
import com.wechatmem.app.data.local.db.VectorEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*

class LocalVectorSearch(private val vectorDao: VectorDao) {

    data class Result(
        val messageId: String,
        val conversationId: String,
        val score: Float
    )

    data class SearchMetadata(
        val timeRange: String,
        val vectorCount: Int,
        val resultCount: Int
    )

    suspend fun search(
        queryEmbedding: FloatArray,
        topK: Int = 10,
        conversationId: String? = null
    ): List<Result> {
        val vectors = if (conversationId != null) {
            vectorDao.getByConversation(conversationId)
        } else {
            vectorDao.getAll()
        }
        return vectors.map { v ->
            Result(v.messageId, v.conversationId, cosine(queryEmbedding, bytesToFloats(v.embedding)))
        }.sortedByDescending { it.score }.take(topK)
    }

    /**
     * 按指定时间范围搜索
     * @param startDate 起始日期，格式 yyyy-MM-dd'T'HH:mm:ss
     */
    suspend fun searchSince(
        queryEmbedding: FloatArray,
        startDate: String,
        topK: Int = 10
    ): Pair<List<Result>, SearchMetadata> {
        val vectors = vectorDao.getVectorsSince(startDate)

        if (vectors.isEmpty()) {
            return emptyList<Result>() to SearchMetadata("无结果", 0, 0)
        }

        val results = vectors.map { v ->
            Result(
                v.messageId,
                v.conversationId,
                cosine(queryEmbedding, bytesToFloats(v.embedding))
            )
        }.sortedByDescending { it.score }.take(topK)

        val metadata = SearchMetadata(
            timeRange = "指定时间范围",
            vectorCount = vectors.size,
            resultCount = results.size
        )

        Log.d("LocalVectorSearch", "Found ${results.size} results since $startDate (searched ${vectors.size} vectors)")
        return results to metadata
    }

    /**
     * 时间范围阶梯搜索
     * 先搜索最近的对话，如果结果不够，逐步扩大范围
     */
    suspend fun searchWithTimeRange(
        queryEmbedding: FloatArray,
        topK: Int = 10,
        minResults: Int = 5
    ): Pair<List<Result>, SearchMetadata> {
        val timeRanges = listOf(
            90 to "最近3个月",
            180 to "最近6个月",
            365 to "最近1年",
            Int.MAX_VALUE to "全部历史"
        )

        for ((days, label) in timeRanges) {
            val vectors = if (days == Int.MAX_VALUE) {
                vectorDao.getAll()
            } else {
                val startDate = getDateBefore(days)
                vectorDao.getVectorsSince(startDate)
            }

            if (vectors.isEmpty()) continue

            val results = vectors.map { v ->
                Result(
                    v.messageId,
                    v.conversationId,
                    cosine(queryEmbedding, bytesToFloats(v.embedding))
                )
            }.sortedByDescending { it.score }.take(topK)

            val metadata = SearchMetadata(
                timeRange = label,
                vectorCount = vectors.size,
                resultCount = results.size
            )

            if (results.size >= minResults || days == Int.MAX_VALUE) {
                Log.d("LocalVectorSearch", "Found ${results.size} results in $label (searched ${vectors.size} vectors)")
                return results to metadata
            }
        }

        return emptyList<Result>() to SearchMetadata("无结果", 0, 0)
    }

    private fun getDateBefore(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            .format(cal.time)
    }

    companion object {
        fun floatsToBytes(floats: FloatArray): ByteArray {
            val buf = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            floats.forEach { buf.putFloat(it) }
            return buf.array()
        }

        fun bytesToFloats(bytes: ByteArray): FloatArray {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(bytes.size / 4) { buf.float }
        }

        private fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f
            var dot = 0f; var na = 0f; var nb = 0f
            for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
            val denom = kotlin.math.sqrt(na) * kotlin.math.sqrt(nb)
            return if (denom > 0) dot / denom else 0f
        }
    }
}
