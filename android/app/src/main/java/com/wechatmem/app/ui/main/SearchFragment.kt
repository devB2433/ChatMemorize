package com.wechatmem.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wechatmem.app.R
import com.wechatmem.app.data.model.SearchResult
import com.wechatmem.app.data.repository.StorageManager
import com.wechatmem.app.databinding.FragmentSearchBinding
import com.wechatmem.app.ui.detail.DetailActivity
import com.wechatmem.app.ui.search.SearchResultAdapter
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SearchResultAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SearchResultAdapter { openConversation(it) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnSearch.setOnClickListener { performSearch() }
        binding.etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { performSearch(); true } else false
        }
    }

    private fun performSearch() {
        val query = binding.etQuery.text?.toString()?.trim() ?: return
        if (query.isEmpty()) return
        binding.progressBar.visibility = View.VISIBLE
        binding.cardAiAnswer.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val repo = StorageManager.getRepository(requireContext())
                val resp = repo.ask(query)
                adapter.submitList(resp.sources)
                if (!resp.answer.startsWith("（")) {
                    binding.cardAiAnswer.visibility = View.VISIBLE
                    binding.tvAiAnswer.text = resp.answer
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun openConversation(result: SearchResult) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_CONVERSATION_ID, result.conversationId)
            putExtra(DetailActivity.EXTRA_TITLE, result.sender)
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
