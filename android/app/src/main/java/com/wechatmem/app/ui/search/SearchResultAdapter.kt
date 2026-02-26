package com.wechatmem.app.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wechatmem.app.R
import com.wechatmem.app.data.model.SearchResult
import com.wechatmem.app.databinding.ItemSearchResultBinding

class SearchResultAdapter(
    private val onClick: (SearchResult) -> Unit
) : ListAdapter<SearchResult, SearchResultAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchResult) {
            binding.tvSender.text = item.sender
            binding.tvContent.text = item.content
            binding.tvRelevance.text =
                binding.root.context.getString(R.string.label_relevance, item.score)
            binding.tvTimestamp.text = item.timestamp ?: ""
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SearchResult>() {
            override fun areItemsTheSame(a: SearchResult, b: SearchResult) =
                a.messageId == b.messageId

            override fun areContentsTheSame(a: SearchResult, b: SearchResult) = a == b
        }
    }
}
