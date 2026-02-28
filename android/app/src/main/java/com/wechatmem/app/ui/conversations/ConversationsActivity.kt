package com.wechatmem.app.ui.conversations

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wechatmem.app.R
import com.wechatmem.app.data.local.AppPrefs
import com.wechatmem.app.data.model.ConversationBrief
import com.wechatmem.app.data.remote.ApiService
import com.wechatmem.app.data.repository.StorageManager
import com.wechatmem.app.databinding.ActivityConversationsBinding
import com.wechatmem.app.ui.detail.DetailActivity
import com.wechatmem.app.ui.manualimport.ManualImportActivity
import com.wechatmem.app.ui.search.SearchActivity
import com.wechatmem.app.ui.settings.SettingsActivity
import com.wechatmem.app.ui.login.LoginActivity
import kotlinx.coroutines.launch

class ConversationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationsBinding
    private lateinit var adapter: ConversationAdapter

    private var currentPage = 1
    private var totalItems = 0
    private var isLoading = false
    private val pageSize = 20

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        if (!StorageManager.isLocal(this) && !AppPrefs.isConfigured(this)) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = getString(R.string.label_not_configured)
            binding.recyclerView.visibility = View.GONE
            return
        }
        loadConversations(page = 1)
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter { conversation ->
            openDetail(conversation)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Pagination: load more when scrolled to bottom
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val lm = rv.layoutManager as LinearLayoutManager
                val visibleCount = lm.childCount
                val totalCount = lm.itemCount
                val firstVisible = lm.findFirstVisibleItemPosition()

                if (!isLoading && (visibleCount + firstVisible) >= totalCount - 2) {
                    val totalPages = (totalItems + pageSize - 1) / pageSize
                    if (currentPage < totalPages) {
                        loadConversations(currentPage + 1)
                    }
                }
            }
        })
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            loadConversations(page = 1)
        }
        binding.btnSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.fabImport.setOnClickListener {
            startActivity(Intent(this, ManualImportActivity::class.java))
        }
    }

    private fun handleUnauthorized() {
        AppPrefs.logout(this)
        ApiService.invalidate()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun loadConversations(page: Int) {
        if (isLoading) return
        isLoading = true

        lifecycleScope.launch {
            try {
                val repo = StorageManager.getRepository(this@ConversationsActivity)
                val result = repo.getConversations(page = page, pageSize = pageSize)

                if (page == 1) {
                    adapter.submitList(result.items)
                } else {
                    val current = adapter.currentList.toMutableList()
                    current.addAll(result.items)
                    adapter.submitList(current)
                }

                currentPage = result.page
                totalItems = result.total

                binding.tvPageInfo.text =
                    getString(R.string.label_page_info, currentPage, totalItems)

                val hasData = adapter.currentList.isNotEmpty()
                binding.recyclerView.visibility = if (hasData) View.VISIBLE else View.GONE
                binding.tvEmpty.visibility = if (hasData) View.GONE else View.VISIBLE
                if (!hasData) binding.tvEmpty.text = getString(R.string.label_no_data)

            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() in listOf(401, 403)) {
                    handleUnauthorized()
                    return@launch
                }
                Toast.makeText(
                    this@ConversationsActivity,
                    "加载失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isLoading = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun openDetail(conversation: ConversationBrief) {
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_CONVERSATION_ID, conversation.id)
            putExtra(DetailActivity.EXTRA_TITLE, conversation.title
                ?: conversation.participants.joinToString(", "))
        }
        startActivity(intent)
    }
}
