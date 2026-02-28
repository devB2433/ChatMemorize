package com.wechatmem.app.ui.summary

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wechatmem.app.data.repository.StorageManager
import com.wechatmem.app.databinding.ActivitySummaryListBinding
import com.wechatmem.app.ui.detail.DetailActivity
import kotlinx.coroutines.launch

class SummaryListActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySummaryListBinding
    private lateinit var adapter: SummaryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySummaryListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationIcon(com.google.android.material.R.drawable.ic_arrow_back_black_24)
        binding.toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, com.wechatmem.app.R.color.on_primary))
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = SummaryAdapter { conv ->
            startActivity(Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_CONVERSATION_ID, conv.id)
                putExtra(DetailActivity.EXTRA_TITLE, conv.title ?: conv.participants.joinToString(", "))
            })
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        loadSummaries()
    }

    private fun loadSummaries() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // Load up to 200 conversations and filter those with summaries
                val result = StorageManager.getRepository(this@SummaryListActivity)
                    .getConversations(1, 200)
                val withSummary = result.items.filter { !it.summary.isNullOrBlank() }
                adapter.submitList(withSummary)
                binding.recyclerView.visibility = if (withSummary.isNotEmpty()) View.VISIBLE else View.GONE
                binding.tvEmpty.visibility = if (withSummary.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(this@SummaryListActivity, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}
