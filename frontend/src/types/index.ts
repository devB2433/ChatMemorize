export interface TokenResponse {
  token: string
  token_type: string
}

export interface UserOut {
  id: string
  username: string
  created_at: string
}

export interface ConversationBrief {
  id: string
  title: string | null
  participants: string[]
  message_count: number
  summary: string | null
  created_at: string
  updated_at: string
}

export interface MessageOut {
  id: string
  sender: string
  content: string
  timestamp: string | null
  sequence: number
  msg_type: string
  image_url: string | null
}

export interface ConversationDetail extends ConversationBrief {
  messages: MessageOut[]
}

export interface PaginatedConversations {
  items: ConversationBrief[]
  total: number
  page: number
  page_size: number
}

export interface SearchResult {
  message_id: string
  conversation_id: string
  sender: string
  content: string
  timestamp: string | null
  score: number
}

export interface SearchResponse {
  results: SearchResult[]
  query: string
}

export interface AskResponse {
  answer: string
  sources: SearchResult[]
}
