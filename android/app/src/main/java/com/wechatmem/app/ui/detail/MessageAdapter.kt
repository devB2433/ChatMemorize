package com.wechatmem.app.ui.detail

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wechatmem.app.R
import com.wechatmem.app.data.model.MessageOut
import com.wechatmem.app.databinding.ItemMessageBinding

class MessageAdapter(
    private val selfSender: String? = null
) : ListAdapter<MessageOut, MessageAdapter.ViewHolder>(DIFF) {

    // First participant is treated as "self" for bubble alignment
    var firstParticipant: String? = selfSender

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: MessageOut) {
            val isSelf = msg.sender == firstParticipant

            binding.tvSender.text = msg.sender
            binding.tvContent.text = msg.content
            binding.tvTimestamp.text = msg.timestamp ?: ""

            // Bubble alignment
            val params = binding.bubbleContainer.layoutParams as FrameLayout.LayoutParams
            if (isSelf) {
                params.gravity = Gravity.END
                binding.tvSender.gravity = Gravity.END
                binding.cardBubble.setCardBackgroundColor(
                    binding.root.context.getColor(R.color.bubble_self)
                )
            } else {
                params.gravity = Gravity.START
                binding.tvSender.gravity = Gravity.START
                binding.cardBubble.setCardBackgroundColor(
                    binding.root.context.getColor(R.color.bubble_other)
                )
            }
            binding.bubbleContainer.layoutParams = params
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MessageOut>() {
            override fun areItemsTheSame(a: MessageOut, b: MessageOut) = a.id == b.id
            override fun areContentsTheSame(a: MessageOut, b: MessageOut) = a == b
        }
    }
}
