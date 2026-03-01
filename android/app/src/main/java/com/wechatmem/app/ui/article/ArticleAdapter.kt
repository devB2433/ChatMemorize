package com.wechatmem.app.ui.article

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wechatmem.app.data.local.db.ArticleEntity
import com.wechatmem.app.databinding.ItemArticleBinding

class ArticleAdapter(
    private val onClick: (ArticleEntity) -> Unit
) : ListAdapter<ArticleEntity, ArticleAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemArticleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemArticleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvTitle.text = item.title ?: item.url
            tvDate.text = item.createdAt.replace("T", " ").take(16)
            tvSummaryPreview.text = item.summary ?: ""
            tvSummaryPreview.visibility = if (item.summary.isNullOrBlank())
                android.view.View.GONE else android.view.View.VISIBLE
            root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ArticleEntity>() {
            override fun areItemsTheSame(a: ArticleEntity, b: ArticleEntity) = a.id == b.id
            override fun areContentsTheSame(a: ArticleEntity, b: ArticleEntity) = a == b
        }
    }
}
