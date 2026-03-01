package com.wechatmem.app.ui.receive

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.wechatmem.app.R
import com.wechatmem.app.data.local.AppPrefs
import com.wechatmem.app.data.remote.ApiService
import com.wechatmem.app.data.repository.StorageManager
import com.wechatmem.app.databinding.ActivityReceiveBinding
import com.wechatmem.app.parser.WeChatTextParser
import com.wechatmem.app.ui.article.ArticleReceiveActivity
import com.wechatmem.app.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ReceiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiveBinding
    private var sharedText: String = ""
    private var imageUris: List<Uri> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        handleIntent()
        binding.btnUpload.setOnClickListener { uploadConversation() }
    }

    private fun handleIntent() {
        when (intent?.action) {
            Intent.ACTION_SEND -> handleSingleSend()
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleSend()
        }

        if (sharedText.isBlank()) {
            binding.tvSenderCount.text = getString(R.string.label_no_text)
            binding.tvMessageCount.visibility = View.GONE
            binding.btnUpload.isEnabled = false
            return
        }

        val parsed = WeChatTextParser.parse(sharedText)

        binding.tvSenderCount.text =
            getString(R.string.label_parsed_senders, parsed.participants.size)
        val imgInfo = if (imageUris.isNotEmpty()) "，${imageUris.size}张图片" else ""
        binding.tvMessageCount.text =
            getString(R.string.label_parsed_messages, parsed.messages.size) + imgInfo

        binding.chipGroupSenders.removeAllViews()
        for (sender in parsed.participants) {
            val chip = Chip(this).apply {
                text = sender
                isClickable = false
            }
            binding.chipGroupSenders.addView(chip)
        }

        binding.tvRawText.text = sharedText
        binding.btnUpload.isEnabled = parsed.messages.isNotEmpty()
    }

    private fun handleSingleSend() {
        sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

        // Detect WeChat article URL and redirect
        if (sharedText.contains("mp.weixin.qq.com")) {
            val urlRegex = Regex("https?://mp\\.weixin\\.qq\\.com/\\S+")
            val url = urlRegex.find(sharedText)?.value
            if (url != null) {
                startActivity(Intent(this, ArticleReceiveActivity::class.java).apply {
                    putExtra("url", url)
                })
                finish()
                return
            }
        }

        // Single image may come via EXTRA_STREAM
        val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (streamUri != null) {
            imageUris = listOf(streamUri)
        }
    }

    @Suppress("DEPRECATION")
    private fun handleMultipleSend() {
        // Text is in EXTRA_TEXT
        sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

        // Multiple images come via EXTRA_STREAM as ArrayList<Uri>
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        if (uris != null) {
            imageUris = uris.toList()
        }
    }

    private fun uploadConversation() {
        binding.btnUpload.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                if (AppPrefs.isLocalMode(this@ReceiveActivity) || imageUris.isEmpty()) {
                    val repo = StorageManager.getRepository(this@ReceiveActivity)
                    repo.createConversation(text = sharedText, title = null)
                } else {
                    val api = ApiService.getInstance(this@ReceiveActivity)
                    val textPart = sharedText.toRequestBody("text/plain".toMediaType())
                    val imageParts = withContext(Dispatchers.IO) {
                        imageUris.mapIndexed { index, uri ->
                            val bytes = contentResolver.openInputStream(uri)?.readBytes()
                                ?: return@mapIndexed null
                            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                            val body = bytes.toRequestBody(mimeType.toMediaType())
                            MultipartBody.Part.createFormData(
                                "images", "image_$index.jpg", body
                            )
                        }.filterNotNull()
                    }
                    api.uploadConversation(textPart, null, imageParts)
                }

                Toast.makeText(
                    this@ReceiveActivity,
                    getString(R.string.label_upload_success),
                    Toast.LENGTH_SHORT
                ).show()

                val navIntent = Intent(
                    this@ReceiveActivity,
                    MainActivity::class.java
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(navIntent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ReceiveActivity,
                    "${getString(R.string.label_upload_fail)}: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                binding.btnUpload.isEnabled = true
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}
