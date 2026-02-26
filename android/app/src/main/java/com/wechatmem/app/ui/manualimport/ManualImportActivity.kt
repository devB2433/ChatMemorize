package com.wechatmem.app.ui.manualimport

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wechatmem.app.R
import com.wechatmem.app.data.model.ConversationCreate
import com.wechatmem.app.data.remote.ApiService
import com.wechatmem.app.databinding.ActivityManualImportBinding
import com.wechatmem.app.parser.WeChatTextParser
import com.wechatmem.app.ui.conversations.ConversationsActivity
import kotlinx.coroutines.launch

class ManualImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManualImportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManualImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationIcon(
            com.google.android.material.R.drawable.ic_arrow_back_black_24
        )
        binding.toolbar.navigationIcon?.setTint(
            ContextCompat.getColor(this, R.color.on_primary)
        )
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnPreview.setOnClickListener { preview() }
        binding.btnUpload.setOnClickListener { upload() }
    }

    private fun preview() {
        val text = binding.etChatText.text?.toString()?.trim() ?: ""
        if (text.isBlank()) {
            Toast.makeText(this, "请粘贴聊天记录", Toast.LENGTH_SHORT).show()
            return
        }

        val parsed = WeChatTextParser.parse(text)
        if (parsed.messages.isEmpty()) {
            binding.cardPreview.visibility = View.VISIBLE
            binding.tvPreviewInfo.text = "未能解析出消息，请检查格式"
            binding.tvPreviewInfo.setTextColor(
                ContextCompat.getColor(this, R.color.error)
            )
            return
        }

        binding.cardPreview.visibility = View.VISIBLE
        binding.tvPreviewInfo.setTextColor(
            ContextCompat.getColor(this, R.color.primary)
        )
        binding.tvPreviewInfo.text = buildString {
            append("识别到 ${parsed.participants.size} 位发送者: ")
            append(parsed.participants.joinToString(", "))
            append("\n共 ${parsed.messages.size} 条消息")
        }
    }

    private fun upload() {
        val text = binding.etChatText.text?.toString()?.trim() ?: ""
        if (text.isBlank()) {
            Toast.makeText(this, "请粘贴聊天记录", Toast.LENGTH_SHORT).show()
            return
        }

        val title = binding.etTitle.text?.toString()?.trim()?.ifBlank { null }

        binding.btnUpload.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val api = ApiService.getInstance(this@ManualImportActivity)
                api.createConversation(
                    ConversationCreate(text = text, title = title)
                )

                Toast.makeText(
                    this@ManualImportActivity,
                    getString(R.string.label_upload_success),
                    Toast.LENGTH_SHORT
                ).show()

                val intent = Intent(
                    this@ManualImportActivity,
                    ConversationsActivity::class.java
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ManualImportActivity,
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
