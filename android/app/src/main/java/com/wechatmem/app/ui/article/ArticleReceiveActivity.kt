package com.wechatmem.app.ui.article

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wechatmem.app.R
import com.wechatmem.app.data.local.AppPrefs
import com.wechatmem.app.data.local.db.AppDatabase
import com.wechatmem.app.data.local.db.ArticleEntity
import com.wechatmem.app.data.remote.LlmClient
import com.wechatmem.app.databinding.ActivityArticleReceiveBinding
import com.wechatmem.app.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class ArticleReceiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArticleReceiveBinding
    private var url: String = ""
    private var extractedTitle: String = ""
    private var extractedContent: String = ""
    private var extractionDone = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArticleReceiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        url = intent.getStringExtra("url") ?: run { finish(); return }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvUrl.text = url
        binding.btnSave.isEnabled = false
        binding.btnSave.setOnClickListener { saveArticle() }

        setupWebView()
        binding.webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel 6) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/112.0.0.0 Mobile Safari/537.36 MicroMessenger/8.0.40"
        }
        binding.webView.addJavascriptInterface(JsBridge(), "Android")
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("""
                    (function() {
                        var title = document.getElementById('activity-name')?.innerText?.trim() || document.title || '';
                        var content = document.getElementById('js_content')?.innerText?.trim() || '';
                        Android.onExtracted(title, content);
                    })();
                """.trimIndent(), null)
            }
        }
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onExtracted(title: String, content: String) {
            runOnUiThread {
                extractedTitle = title
                extractedContent = content
                extractionDone = true
                binding.tvStatus.text = if (content.isNotBlank())
                    getString(R.string.article_extracted, title.take(30))
                else
                    getString(R.string.article_extract_failed)
                binding.btnSave.isEnabled = true
            }
        }
    }

    private fun saveArticle() {
        binding.btnSave.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.article_saving)

        lifecycleScope.launch {
            try {
                val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val id = UUID.randomUUID().toString()
                val db = AppDatabase.getInstance(this@ArticleReceiveActivity)

                val entity = ArticleEntity(
                    id = id,
                    url = url,
                    title = extractedTitle.ifBlank { null },
                    content = extractedContent.ifBlank { null },
                    summary = null,
                    createdAt = now,
                    updatedAt = now
                )
                withContext(Dispatchers.IO) { db.articleDao().insert(entity) }

                // Fire-and-forget summary generation
                val apiKey = AppPrefs.getZhipuApiKey(this@ArticleReceiveActivity)
                if (apiKey.isNotBlank() && extractedContent.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val model = AppPrefs.getLlmModel(this@ArticleReceiveActivity)
                            val summary = LlmClient(apiKey, model)
                                .summarizeArticle(extractedTitle, extractedContent.take(3000))
                            db.articleDao().updateSummary(id, summary,
                                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        } catch (_: Exception) {}
                    }
                }

                Toast.makeText(this@ArticleReceiveActivity,
                    getString(R.string.article_saved), Toast.LENGTH_SHORT).show()

                startActivity(Intent(this@ArticleReceiveActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                })
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@ArticleReceiveActivity,
                    "${getString(R.string.label_upload_fail)}: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnSave.isEnabled = true
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}
