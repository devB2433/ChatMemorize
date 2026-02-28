package com.wechatmem.app.ui.detail

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wechatmem.app.R
import com.wechatmem.app.data.repository.StorageManager
import com.wechatmem.app.databinding.ActivityDetailBinding
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var adapter: MessageAdapter
    private var conversationId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.title_detail)
        binding.toolbar.title = title
        binding.toolbar.setNavigationIcon(com.google.android.material.R.drawable.ic_arrow_back_black_24)
        binding.toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.on_primary))
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = MessageAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnGenerateSummary.setOnClickListener { generateSummary() }

        loadDetail()
    }

    private fun loadDetail() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val repo = StorageManager.getRepository(this@DetailActivity)
                val detail = repo.getConversation(conversationId)

                // Set first participant for bubble alignment
                if (detail.participants.isNotEmpty()) {
                    adapter.firstParticipant = detail.participants.first()
                }

                adapter.submitList(detail.messages)

                // Show summary if available
                if (!detail.summary.isNullOrBlank()) {
                    binding.cardSummary.visibility = View.VISIBLE
                    binding.tvSummary.text = detail.summary
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@DetailActivity,
                    "加载失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.label_confirm)) { _, _ ->
                deleteConversation()
            }
            .setNegativeButton(getString(R.string.label_cancel), null)
            .show()
    }

    private fun deleteConversation() {
        lifecycleScope.launch {
            try {
                val repo = StorageManager.getRepository(this@DetailActivity)
                repo.deleteConversation(conversationId)
                Toast.makeText(this@DetailActivity, "已删除", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@DetailActivity,
                    "删除失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun generateSummary() {
        binding.btnGenerateSummary.isEnabled = false
        binding.cardSummary.visibility = View.VISIBLE
        binding.tvSummary.text = "生成中…"
        lifecycleScope.launch {
            try {
                val repo = StorageManager.getRepository(this@DetailActivity)
                val summary = repo.generateSummary(conversationId)
                binding.tvSummary.text = summary
            } catch (e: Exception) {
                binding.tvSummary.text = "生成失败: ${e.message}"
            } finally {
                binding.btnGenerateSummary.isEnabled = true
            }
        }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_TITLE = "title"
    }
}
