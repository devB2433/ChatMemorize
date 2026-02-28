import type { ConversationDetail } from '@/types'

export function exportAsMarkdown(conv: ConversationDetail): void {
  const lines: string[] = []
  lines.push(`# ${conv.title || '未命名会话'}`)
  lines.push('')
  lines.push(`参与者: ${conv.participants.join(', ')}`)
  lines.push(`消息数: ${conv.message_count}`)
  lines.push(`创建时间: ${conv.created_at}`)
  lines.push('')

  if (conv.summary) {
    lines.push('## 摘要')
    lines.push('')
    lines.push(conv.summary)
    lines.push('')
  }

  lines.push('## 聊天记录')
  lines.push('')

  for (const msg of conv.messages) {
    const time = msg.timestamp ? `[${msg.timestamp}] ` : ''
    if (msg.msg_type === 'image') {
      lines.push(`${time}**${msg.sender}**: [图片]`)
    } else {
      lines.push(`${time}**${msg.sender}**: ${msg.content}`)
    }
  }

  const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${conv.title || 'conversation'}.md`
  a.click()
  URL.revokeObjectURL(url)
}
