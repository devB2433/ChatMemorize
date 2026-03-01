package com.wechatmem.app.ui.article

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wechatmem.app.R
import com.wechatmem.app.data.local.AppPrefs
import com.wechatmem.app.data.local.db.AppDatabase
import com.wechatmem.app.data.remote.LlmClient
import com.wechatmem.app.databinding.ActivityArticleDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ArticleDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArticleDetailBinding
    private var articleId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArticleDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        articleId = intent.getStringExtra("id") ?: run { finish(); return }
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnGenerateSummary.setOnClickListener { generateSummary() }

        loadArticle()
    }

    private fun loadArticle() {
        lifecycleScope.launch {
            val article = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@ArticleDetailActivity).articleDao().getById(articleId)
            } ?: run { finish(); return@launch }

            binding.tvTitle.text = article.title ?: getString(R.string.article_no_title)
            binding.tvDate.text = article.createdAt.replace("T", " ").take(16)
            binding.tvUrl.text = article.url
            binding.tvUrl.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url)))
            }
            if (!article.summary.isNullOrBlank()) {
                binding.cardSummary.visibility = View.VISIBLE
                binding.tvSummary.text = article.summary
            }
            binding.tvContent.text = article.content ?: getString(R.string.article_no_content)
        }
    }

    private fun generateSummary() {
        binding.btnGenerateSummary.isEnabled = false
        binding.cardSummary.visibility = View.VISIBLE
        binding.tvSummary.text = getString(R.string.generating)

        lifecycleScope.launch {
            try {
                val article = withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(this@ArticleDetailActivity).articleDao().getById(articleId)
                } ?: return@launch

                val apiKey = AppPrefs.getZhipuApiKey(this@ArticleDetailActivity)
                if (apiKey.isBlank()) {
                    binding.tvSummary.text = getString(R.string.error_no_api_key)
                    return@launch
                }
                val model = AppPrefs.getLlmModel(this@ArticleDetailActivity)
                val content = article.content ?: ""
                val title = article.title ?: ""
                val summary = withContext(Dispatchers.IO) {
                    LlmClient(apiKey, model).summarizeArticle(title, content.take(3000))
                }
                val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(this@ArticleDetailActivity)
                        .articleDao().updateSummary(articleId, summary, now)
                }
                binding.tvSummary.text = summary
            } catch (e: Exception) {
                binding.tvSummary.text = "${getString(R.string.error_generate_failed)}: ${e.message}"
                Toast.makeText(this@ArticleDetailActivity, e.message, Toast.LENGTH_LONG).show()
            } finally {
                binding.btnGenerateSummary.isEnabled = true
            }
        }
    }
}
