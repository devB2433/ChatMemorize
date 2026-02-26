package com.wechatmem.app.ui.conversations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wechatmem.app.data.model.ConversationBrief
import com.wechatmem.app.databinding.ItemConversationBinding

class ConversationAdapter(
    private val onClick: (ConversationBrief) -> Unit
) : ListAdapter<ConversationBrief, ConversationAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemConversationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ConversationBrief) {
            binding.tvTitle.text = item.title ?: item.participants.joinToString(", ")
            binding.tvParticipants.text = item.participants.joinToString(", ")
            binding.tvMessageCount.text = "${item.messageCount} 条消息"
            binding.tvDate.text = item.createdAt.take(10)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ConversationBrief>() {
            override fun areItemsTheSame(a: ConversationBrief, b: ConversationBrief) =
                a.id == b.id

            override fun areContentsTheSame(a: ConversationBrief, b: ConversationBrief) =
                a == b
        }
    }
}
