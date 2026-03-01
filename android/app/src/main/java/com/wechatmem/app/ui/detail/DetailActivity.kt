package com.wechatmem.app.ui.detail

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.wechatmem.app.R
import com.wechatmem.app.data.local.db.AppDatabase
import com.wechatmem.app.data.local.db.ConversationTagEntity
import com.wechatmem.app.data.local.db.TagEntity
import com.wechatmem.app.data.repository.StorageManager
import com.wechatmem.app.databinding.ActivityDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var adapter: MessageAdapter
    private var conversationId: String = ""

    private val TAG_PALETTE = listOf("#E53935", "#8E24AA", "#1E88E5", "#00897B", "#F4511E", "#F6BF26", "#0B8043")

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
        binding.btnAddTag.setOnClickListener { showTagDialog() }

        loadDetail()
        loadTags()
    }

    private fun loadDetail() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val repo = StorageManager.getRepository(this@DetailActivity)
                val detail = repo.getConversation(conversationId)
                if (detail.participants.isNotEmpty()) adapter.firstParticipant = detail.participants.first()
                adapter.submitList(detail.messages)
                if (!detail.summary.isNullOrBlank()) {
                    binding.cardSummary.visibility = View.VISIBLE
                    binding.tvSummary.text = detail.summary
                }
            } catch (e: Exception) {
                Toast.makeText(this@DetailActivity, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadTags() {
        lifecycleScope.launch {
            val tags = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@DetailActivity).tagDao().getTagsForConversation(conversationId)
            }
            binding.chipGroupTags.removeAllViews()
            for (tag in tags) {
                val color = try { Color.parseColor(tag.colorHex) } catch (_: Exception) { Color.GRAY }
                val bg = Color.argb(38, Color.red(color), Color.green(color), Color.blue(color))
                val chip = Chip(this@DetailActivity).apply {
                    text = tag.name
                    isCloseIconVisible = true
                    chipBackgroundColor = ColorStateList.valueOf(bg)
                    setTextColor(color)
                    closeIconTint = ColorStateList.valueOf(color)
                    chipStrokeWidth = 0f
                }
                chip.setOnCloseIconClickListener {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(this@DetailActivity).tagDao()
                                .deleteConversationTag(ConversationTagEntity(conversationId, tag.id))
                        }
                        loadTags()
                    }
                }
                binding.chipGroupTags.addView(chip)
            }
        }
    }

    private fun showTagDialog() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@DetailActivity)
            val allTags = withContext(Dispatchers.IO) { db.tagDao().getAll() }
            val currentIds = withContext(Dispatchers.IO) {
                db.tagDao().getTagsForConversation(conversationId).map { it.id }.toSet()
            }
            val names = allTags.map { it.name }.toTypedArray()
            val checked = allTags.map { it.id in currentIds }.toBooleanArray()

            AlertDialog.Builder(this@DetailActivity)
                .setTitle(getString(R.string.title_select_tag))
                .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                    val tag = allTags[which]
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (isChecked) db.tagDao().insertConversationTag(ConversationTagEntity(conversationId, tag.id))
                        else db.tagDao().deleteConversationTag(ConversationTagEntity(conversationId, tag.id))
                    }
                }
                .setNeutralButton(getString(R.string.btn_new_tag)) { _, _ -> showCreateTagDialog() }
                .setPositiveButton(getString(R.string.label_confirm)) { _, _ -> loadTags() }
                .show()
        }
    }

    private fun showCreateTagDialog() {
        val input = EditText(this).apply { hint = getString(R.string.hint_tag_name) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.btn_new_tag))
            .setView(input)
            .setPositiveButton(getString(R.string.label_confirm)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    lifecycleScope.launch {
                        val db = AppDatabase.getInstance(this@DetailActivity)
                        val count = withContext(Dispatchers.IO) { db.tagDao().getAll().size }
                        val color = TAG_PALETTE[count % TAG_PALETTE.size]
                        withContext(Dispatchers.IO) {
                            db.tagDao().insert(TagEntity(UUID.randomUUID().toString(), name, color))
                        }
                        showTagDialog()
                    }
                }
            }
            .setNegativeButton(getString(R.string.label_cancel), null)
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.label_confirm)) { _, _ -> deleteConversation() }
            .setNegativeButton(getString(R.string.label_cancel), null)
            .show()
    }

    private fun deleteConversation() {
        lifecycleScope.launch {
            try {
                StorageManager.getRepository(this@DetailActivity).deleteConversation(conversationId)
                Toast.makeText(this@DetailActivity, "已删除", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@DetailActivity, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateSummary() {
        binding.btnGenerateSummary.isEnabled = false
        binding.cardSummary.visibility = View.VISIBLE
        binding.tvSummary.text = "生成中…"
        lifecycleScope.launch {
            try {
                val summary = StorageManager.getRepository(this@DetailActivity).generateSummary(conversationId)
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
