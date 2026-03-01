package com.wechatmem.app.ui.main

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.wechatmem.app.R
import com.wechatmem.app.data.local.db.AppDatabase
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
    private var selectedTagId: String? = null

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

    override fun onResume() {
        super.onResume()
        if (StorageManager.isLocal(requireContext())) loadTagFilters()
        else binding.scrollTags.visibility = View.GONE
    }

    private fun loadTagFilters() {
        viewLifecycleOwner.lifecycleScope.launch {
            val tags = AppDatabase.getInstance(requireContext()).tagDao().getAll()
            if (tags.isEmpty()) { binding.scrollTags.visibility = View.GONE; return@launch }
            binding.scrollTags.visibility = View.VISIBLE
            binding.chipGroupTags.removeAllViews()
            for (tag in tags) {
                val color = try { Color.parseColor(tag.colorHex) } catch (_: Exception) { Color.GRAY }
                val chip = Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                    text = tag.name
                    isCheckable = true
                    isChecked = selectedTagId == tag.id
                    val bg = Color.argb(38, Color.red(color), Color.green(color), Color.blue(color))
                    chipBackgroundColor = ColorStateList.valueOf(if (isChecked) color else bg)
                    setTextColor(if (isChecked) Color.WHITE else color)
                    chipStrokeWidth = 0f
                }
                chip.setOnCheckedChangeListener { _, checked ->
                    selectedTagId = if (checked) tag.id else null
                }
                binding.chipGroupTags.addView(chip)
            }
        }
    }

    private fun performSearch() {
        val query = binding.etQuery.text?.toString()?.trim() ?: return
        if (query.isEmpty()) return
        binding.layoutLoading.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.cardAiAnswer.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val repo = StorageManager.getRepository(requireContext())
                val resp = repo.ask(query)
                val tagId = selectedTagId
                val results = if (tagId != null)
                    resp.sources.filter { it.conversationId in
                        AppDatabase.getInstance(requireContext()).tagDao().getConversationIdsByTag(tagId) }
                else resp.sources
                adapter.submitList(results)
                binding.recyclerView.visibility = View.VISIBLE
                if (!resp.answer.startsWith("（")) {
                    binding.cardAiAnswer.visibility = View.VISIBLE
                    binding.tvAiAnswer.text = resp.answer
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.layoutLoading.visibility = View.GONE
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
