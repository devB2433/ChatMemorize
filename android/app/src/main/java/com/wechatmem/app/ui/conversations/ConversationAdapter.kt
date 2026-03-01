package com.wechatmem.app.ui.conversations

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.wechatmem.app.R
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
            binding.tvParticipants.text = item.participants.joinToString(" · ")
            binding.tvMessageCount.text = "${item.messageCount} 条消息"
            binding.tvDate.text = formatTime(item.updatedAt)

            // Summary preview
            if (!item.summary.isNullOrBlank()) {
                binding.tvSummaryPreview.visibility = View.VISIBLE
                binding.tvSummaryPreview.text = item.summary
                binding.viewStatusDot.backgroundTintList =
                    ContextCompat.getColorStateList(binding.root.context, R.color.dot_green)
            } else {
                binding.tvSummaryPreview.visibility = View.GONE
                binding.viewStatusDot.backgroundTintList =
                    ContextCompat.getColorStateList(binding.root.context, R.color.primary)
            }

            binding.root.setOnClickListener { onClick(item) }

            // Tag chips
            binding.chipGroupTags.removeAllViews()
            val tags = item.tags.orEmpty()
            if (tags.isNotEmpty()) {
                binding.chipGroupTags.visibility = View.VISIBLE
                for (tag in tags) {
                    val color = try { Color.parseColor(tag.colorHex) } catch (_: Exception) { Color.GRAY }
                    val bg = Color.argb(38, Color.red(color), Color.green(color), Color.blue(color))
                    val chip = Chip(binding.root.context).apply {
                        text = tag.name
                        isClickable = false
                        isCheckable = false
                        chipBackgroundColor = ColorStateList.valueOf(bg)
                        setTextColor(color)
                        chipStrokeWidth = 0f
                        textSize = 10f
                        chipMinHeight = 24f * resources.displayMetrics.density
                        chipStartPadding = 8f * resources.displayMetrics.density
                        chipEndPadding = 8f * resources.displayMetrics.density
                    }
                    binding.chipGroupTags.addView(chip)
                }
            } else {
                binding.chipGroupTags.visibility = View.GONE
            }
        }

        private fun formatTime(iso: String): String {
            // "2024-01-15T10:30:00" → "2024-01-15 10:30"
            return try {
                iso.replace("T", " ").take(16)
            } catch (_: Exception) { iso.take(10) }
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
