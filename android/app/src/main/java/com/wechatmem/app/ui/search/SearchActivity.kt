package com.wechatmem.app.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.wechatmem.app.R
import com.wechatmem.app.data.model.SearchResult
import com.wechatmem.app.data.repository.StorageManager
import com.wechatmem.app.databinding.ActivitySearchBinding
import com.wechatmem.app.ui.detail.DetailActivity
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: SearchResultAdapter
    private var isAskMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationIcon(com.google.android.material.R.drawable.ic_arrow_back_black_24)
        binding.toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.on_primary))
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        setupTabs()
        setupSearch()
    }

    private fun setupRecyclerView() {
        adapter = SearchResultAdapter { result ->
            openConversation(result)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                isAskMode = tab?.position == 1
                binding.etQuery.hint = if (isAskMode) {
                    getString(R.string.hint_ask_ai)
                } else {
                    getString(R.string.hint_search)
                }
                binding.btnSearch.text = if (isAskMode) {
                    getString(R.string.btn_ask_ai)
                } else {
                    getString(R.string.btn_search)
                }
                // Clear previous results
                adapter.submitList(emptyList())
                binding.cardAiAnswer.visibility = View.GONE
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener { performSearch() }
        binding.etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }
    }

    private fun performSearch() {
        val query = binding.etQuery.text?.toString()?.trim() ?: return
        if (query.isEmpty()) return

        binding.progressBar.visibility = View.VISIBLE
        binding.cardAiAnswer.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val repo = StorageManager.getRepository(this@SearchActivity)
                if (isAskMode) {
                    val response = repo.ask(query)
                    binding.cardAiAnswer.visibility = View.VISIBLE
                    binding.tvAiAnswer.text = response.answer
                    adapter.submitList(response.sources)
                } else {
                    val response = repo.search(query)
                    adapter.submitList(response.results)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SearchActivity,
                    "搜索失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun openConversation(result: SearchResult) {
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_CONVERSATION_ID, result.conversationId)
            putExtra(DetailActivity.EXTRA_TITLE, result.sender)
        }
        startActivity(intent)
    }
}
