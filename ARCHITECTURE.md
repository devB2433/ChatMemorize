# WeChatMem 系统架构文档

## 总体架构

```
┌──────────┐    分享     ┌──────────────────────────────────────────┐
│  微信    │ ─────────> │            Android App                    │
└──────────┘            │                                          │
                        │  StorageManager (Repository Pattern)     │
                        │     ├── CloudRepository → Retrofit API   │
                        │     └── LocalRepository → Room+ONNX+LLM │
                        └──────────┬──────────────────┬────────────┘
                                   │                  │
                        ┌──────────▼──────────┐       │ 直连
                        │    FastAPI 后端      │       │ 智谱GLM
                        │  ┌────────────────┐ │       │
                        │  │ SQLite (关系)  │ │       ▼
                        │  │ ChromaDB (向量)│ │  open.bigmodel.cn
                        │  │ BGE Embedding  │ │
                        │  │ 智谱GLM (LLM)  │ │
                        │  └────────────────┘ │
                        └──────────┬──────────┘
                                   │
                        ┌──────────▼──────────┐
                        │   Vue 3 Web 前端    │
                        └─────────────────────┘
```

## 项目目录结构

```
chatMem/
├── backend/                          # FastAPI 后端
│   ├── app/
│   │   ├── main.py                   # 入口，CORS，lifespan
│   │   ├── config.py                 # 配置 (env prefix: WECHATMEM_)
│   │   ├── database.py               # SQLAlchemy async engine
│   │   ├── limiter.py                # slowapi 限流器
│   │   ├── logging_config.py         # JSON 结构化日志
│   │   ├── api/
│   │   │   ├── auth.py               # 注册/登录/刷新
│   │   │   ├── conversations.py      # 对话 CRUD + 上传
│   │   │   ├── search.py             # 语义搜索 + RAG 问答
│   │   │   └── health.py             # 健康检查
│   │   ├── models/
│   │   │   ├── user.py               # User ORM
│   │   │   ├── conversation.py       # Conversation ORM (user_id FK)
│   │   │   └── message.py            # Message ORM
│   │   ├── schemas/                  # Pydantic 请求/响应模型
│   │   ├── services/
│   │   │   ├── auth.py               # bcrypt + JWT
│   │   │   ├── parser.py             # 微信文本解析
│   │   │   ├── embedding.py          # BGE-small-zh 向量化
│   │   │   ├── vectorstore.py        # ChromaDB 操作
│   │   │   ├── llm.py                # 智谱GLM 调用
│   │   │   └── summary.py            # 摘要生成
│   │   └── middleware/
│   │       └── logging.py            # 请求日志中间件
│   ├── tests/                        # 31 个测试
│   ├── Dockerfile
│   └── requirements.txt
│
├── frontend/                         # Vue 3 前端
│   ├── src/
│   │   ├── api/index.ts              # Axios + 拦截器
│   │   ├── views/
│   │   │   ├── LoginView.vue         # 登录/注册
│   │   │   ├── ConversationList.vue  # 对话列表
│   │   │   ├── ConversationDetail.vue# 详情 + Markdown 导出
│   │   │   └── SearchView.vue        # 搜索 + AI 问答
│   │   ├── components/
│   │   │   ├── ChatBubble.vue        # 聊天气泡
│   │   │   ├── SummaryCard.vue       # 摘要卡片
│   │   │   ├── SearchBar.vue         # 搜索栏
│   │   │   ├── SkeletonLoader.vue    # 骨架屏
│   │   │   └── ToastContainer.vue    # Toast 通知
│   │   ├── stores/                   # Pinia: auth, conversations, toast
│   │   ├── utils/export.ts           # Markdown 导出
│   │   └── router/index.ts           # 路由守卫
│   ├── Dockerfile
│   ├── nginx.conf
│   └── vite.config.ts                # 代理 /api → :8000
│
├── android/                          # Kotlin Android App
│   └── app/src/main/java/com/wechatmem/app/
│       ├── WeChatMemApp.kt           # Application 单例
│       ├── parser/
│       │   └── WeChatTextParser.kt   # 微信文本解析 (正则)
│       ├── data/
│       │   ├── model/Models.kt       # 数据类
│       │   ├── local/
│       │   │   ├── AppPrefs.kt       # SharedPreferences
│       │   │   ├── db/               # Room: Entities, Daos, AppDatabase
│       │   │   ├── embedding/        # ONNX: Tokenizer, Model, Downloader
│       │   │   └── search/           # LocalVectorSearch (余弦相似度)
│       │   ├── remote/
│       │   │   ├── ApiService.kt     # Retrofit 云端 API
│       │   │   └── LlmClient.kt     # OkHttp 直连智谱GLM
│       │   ├── repository/
│       │   │   ├── ConversationRepository.kt  # 接口
│       │   │   ├── CloudRepository.kt         # 云端实现
│       │   │   ├── LocalRepository.kt         # 本地实现
│       │   │   └── StorageManager.kt          # 模式切换
│       │   └── migration/
│       │       └── MigrationService.kt        # 本地→云端迁移
│       └── ui/
│           ├── main/                 # 底部导航主框架
│           │   ├── MainActivity.kt   # BottomNav + Fragment show/hide
│           │   ├── ConversationsFragment.kt
│           │   ├── SearchFragment.kt
│           │   └── ProfileFragment.kt
│           ├── login/LoginActivity.kt
│           ├── detail/DetailActivity.kt
│           ├── receive/ReceiveActivity.kt
│           └── manualimport/ManualImportActivity.kt
│
├── docker-compose.yml
└── start-dev.sh
```

## 数据流

### 云端模式：上传对话

```
用户粘贴/分享文本
  → WeChatTextParser.parse() 本地预览
  → CloudRepository.createConversation()
    → Retrofit POST /api/v1/conversations
      → parser.py 解析
      → SQLite 存储 conversation + messages
      → BGE embedding → ChromaDB 存储向量
      → 智谱GLM 生成摘要 (异步)
  → 返回 ConversationBrief
```

### 本地模式：上传对话

```
用户粘贴/分享文本
  → WeChatTextParser.parse() 本地预览
  → LocalRepository.createConversation()
    → Room 存储 ConversationEntity + MessageEntity
    → ONNX EmbeddingModel.encode() 逐条向量化
    → Room 存储 VectorEntity (ByteArray BLOB)
  → 返回 ConversationBrief
```

### 语义搜索

```
云端: query → Retrofit POST /search → BGE embed → ChromaDB 检索 → 返回 topK
本地: query → EmbeddingModel.encode() → LocalVectorSearch 暴力余弦 → 返回 topK
```

### RAG 问答

```
云端: question → POST /search/ask → 搜索 topK → 拼接 context → 智谱GLM → answer
本地: question → 本地搜索 topK → 拼接 context → LlmClient 直连智谱GLM → answer
```

## 认证体系

```
注册/登录 → bcrypt 验证 → JWT (HS256, 7天过期)
  → Header: Authorization: Bearer <token>
  → FastAPI Depends(get_current_user) 解析
  → 所有 CRUD/搜索路由按 user_id 隔离数据
```

- 密码: bcrypt 哈希 (不用 passlib，直接 bcrypt 库)
- Token: PyJWT, payload 含 user_id + exp
- 前端: Pinia auth store + axios 拦截器自动附加 Token
- Android: AppPrefs 存 token, OkHttp interceptor 附加 Bearer
- 401 响应: 前端路由守卫跳转登录页, Android 清 token 跳 LoginActivity

## Android 双模式架构 (Phase 7)

### Repository Pattern

```
MainActivity (BottomNav: 对话/搜索/我的)
  ├── ConversationsFragment
  ├── SearchFragment
  └── ProfileFragment
  → StorageManager.getRepository(context)
      ├── isLocalMode=true  → LocalRepository
      │     ├── Room (SQLite)        — 结构化存储
      │     ├── ONNX Runtime         — 本地向量化
      │     ├── LocalVectorSearch    — 暴力余弦搜索
      │     └── LlmClient           — 直连智谱GLM (无网络时降级为纯搜索)
      └── isLocalMode=false → CloudRepository
            └── Retrofit ApiService  — 所有操作走后端 API
```

### 本地存储 Schema (Room)

```
conversations          messages                vectors
├── id (PK)            ├── id (PK)             ├── message_id (PK, FK)
├── title              ├── conversation_id(FK) ├── conversation_id
├── participants(JSON) ├── sender              └── embedding (BLOB)
├── message_count      ├── content
├── summary            ├── timestamp
├── created_at         └── sequence
└── updated_at
```

### 本地 Embedding 流程

```
文本 → BertTokenizer (WordPiece, CJK字符级)
     → input_ids + attention_mask + token_type_ids (max_len=128)
     → ONNX Runtime (bge-small-zh-v1.5 量化模型, ~25MB)
     → mean pooling (attention mask 加权)
     → L2 normalize
     → FloatArray → ByteArray (BLOB 存储)
```

### 离线降级策略

- 搜索: 纯本地，不需要网络
- 问答: 尝试调用智谱 GLM API，失败时降级为仅返回搜索结果
- 摘要: 需要网络，无网络时跳过

## 生产加固 (Phase 6)

- **限流**: slowapi, 认证接口 10次/分钟, API 接口 60次/分钟
- **日志**: JSON 结构化日志, 请求日志中间件记录 method/path/status/duration
- **上传校验**: 单文件 ≤10MB, 总计 ≤50MB, 标题 ≤200字符
- **CORS**: `settings.cors_origins_list` 可配置
- **前端 Toast**: 429/5xx 自动弹出错误提示
- **骨架屏**: 列表/详情页加载态 shimmer 动画
- **导出**: Markdown 格式导出对话记录

## Android UX 优化 (Phase 9)

- **搜索/问答合并**: 统一入口，始终调用 RAG 问答；有网络且有实质回答时显示 AI 回答卡片，无网络时仅展示搜索结果，对用户透明
- **App 图标**: 自适应图标 (mipmap-anydpi-v26)，蓝色背景 + 白色气泡 + 三点设计
- **配置页分区**: 「我的」页按 存储模式 / AI 配置 / 云端服务器 / 数据管理 / 账号 五个区块组织，保存按钮固定底部
- **APK 命名**: `applicationVariants` 自动输出 `wechatmem-{versionName}-{buildType}.apk`

## 部署架构

```
docker-compose.yml
├── backend   (Python FastAPI, port 8000)
│   ├── SQLite → /app/data/wechatmem.db
│   └── ChromaDB → /app/data/chroma/
├── frontend  (Nginx, port 80)
│   └── nginx.conf 反向代理 /api → backend:8000
└── env: JWT_SECRET, ZHIPU_API_KEY
```

## 技术决策记录

| 决策 | 选择 | 原因 |
|------|------|------|
| 后端框架 | FastAPI | async 原生支持, 自动 OpenAPI 文档 |
| 关系存储 | SQLite + aiosqlite | 零运维, 单文件, 个人项目足够 |
| 向量存储 | ChromaDB | 嵌入式, Python 原生, 无需外部服务 |
| Embedding | BGE-small-zh-v1.5 | 中文效果好, 模型小 (~90MB) |
| LLM | 智谱 GLM-4-flash | 中文能力强, API 价格低 |
| 密码哈希 | bcrypt (直接) | passlib 与 bcrypt≥4 不兼容 |
| Android 本地 DB | Room | 官方推荐, 编译时校验, 协程支持 |
| Android 本地推理 | ONNX Runtime Mobile | 跨平台, 量化模型体积小 |
| 本地向量搜索 | 暴力余弦 | 万级消息毫秒级, 无需复杂索引 |
| 前端状态 | Pinia | Vue 3 官方推荐, TypeScript 友好 |
| Android 导航 | BottomNav + Fragment show/hide | 保留滚动状态, 避免 replace 重建 |
| 默认存储模式 | 本地 | 零配置即用, 无需服务器 |
