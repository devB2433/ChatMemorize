package com.wechatmem.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wechatmem.app.R
import com.wechatmem.app.data.local.AppPrefs
import com.wechatmem.app.data.model.ConversationBrief
import com.wechatmem.app.data.remote.ApiService
import com.wechatmem.app.data.repository.StorageManager
import com.wechatmem.app.databinding.FragmentConversationsBinding
import com.wechatmem.app.ui.conversations.ConversationAdapter
import com.wechatmem.app.ui.detail.DetailActivity
import com.wechatmem.app.ui.login.LoginActivity
import com.wechatmem.app.ui.manualimport.ManualImportActivity
import kotlinx.coroutines.launch

class ConversationsFragment : Fragment() {

    private var _binding: FragmentConversationsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ConversationAdapter

    private var currentPage = 1
    private var totalItems = 0
    private var isLoading = false
    private val pageSize = 20

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConversationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        binding.swipeRefresh.setOnRefreshListener { loadConversations(1) }
        binding.fabImport.setOnClickListener {
            startActivity(Intent(requireContext(), ManualImportActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val ctx = requireContext()
        if (!StorageManager.isLocal(ctx) && !AppPrefs.isConfigured(ctx)) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = getString(R.string.label_not_configured)
            binding.recyclerView.visibility = View.GONE
            return
        }
        loadConversations(1)
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter { openDetail(it) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                if (!isLoading && (lm.childCount + lm.findFirstVisibleItemPosition()) >= lm.itemCount - 2) {
                    val totalPages = (totalItems + pageSize - 1) / pageSize
                    if (currentPage < totalPages) loadConversations(currentPage + 1)
                }
            }
        })
    }

    private fun loadConversations(page: Int) {
        if (isLoading) return
        isLoading = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = StorageManager.getRepository(requireContext())
                    .getConversations(page, pageSize)
                if (page == 1) adapter.submitList(result.items)
                else adapter.submitList(adapter.currentList + result.items)
                currentPage = result.page
                totalItems = result.total
                binding.tvPageInfo.text = getString(R.string.label_page_info, currentPage, totalItems)
                val hasData = adapter.currentList.isNotEmpty()
                binding.recyclerView.visibility = if (hasData) View.VISIBLE else View.GONE
                binding.tvEmpty.visibility = if (hasData) View.GONE else View.VISIBLE
                if (!hasData) binding.tvEmpty.text = getString(R.string.label_no_data)
            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() in listOf(401, 403)) {
                    AppPrefs.logout(requireContext())
                    ApiService.invalidate()
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    activity?.finish()
                    return@launch
                }
                Toast.makeText(requireContext(), "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun openDetail(c: ConversationBrief) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_CONVERSATION_ID, c.id)
            putExtra(DetailActivity.EXTRA_TITLE, c.title ?: c.participants.joinToString(", "))
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
