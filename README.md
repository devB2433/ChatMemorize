# WeChatMem - 微信聊天记忆系统

微信聊天记录的语义搜索与智能摘要平台。从微信分享聊天记录到安卓App，自动解析、向量化存储，支持语义搜索和AI问答。

## 架构

```
[微信] --分享--> [Android App] --REST API--> [FastAPI 后端]
                                                ├── SQLite (结构化存储)
                                                ├── ChromaDB (向量存储)
                                                ├── BGE-small-zh (本地embedding)
                                                └── 智谱GLM (摘要/RAG)
                                                    ↑
                                          [Vue 3 Web前端] (管理/浏览)
```

## 快速开始

### 后端

```bash
cd backend
pip install -r requirements.txt

# 可选：配置智谱API（用于摘要和RAG问答）
echo "WECHATMEM_ZHIPU_API_KEY=your_key_here" > .env

# 启动
uvicorn app.main:app --reload
```

API 运行在 `http://localhost:8000`，文档在 `/docs`。

### Web前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:3000`，开发服务器自动代理API请求到后端。

### 安卓App

用 Android Studio 打开 `android/` 目录，Gradle同步后即可构建运行。

默认连接 `http://10.0.2.2:8000`（模拟器访问宿主机），真机调试需修改 `ApiService.kt` 中的 BASE_URL。

## 使用流程

1. 在微信中选择聊天记录 → 转发 → 分享到 WeChatMem App
2. App 解析预览后上传到后端
3. 后端自动：存入SQLite → BGE向量化存入ChromaDB → 智谱GLM生成摘要
4. 通过App或Web端浏览、语义搜索、AI问答

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/health` | 健康检查 |
| `POST` | `/api/v1/conversations` | 上传对话（传入微信原始文本） |
| `GET` | `/api/v1/conversations` | 对话列表（支持分页和关键词过滤） |
| `GET` | `/api/v1/conversations/{id}` | 对话详情（含全部消息） |
| `PATCH` | `/api/v1/conversations/{id}` | 更新标题 |
| `DELETE` | `/api/v1/conversations/{id}` | 删除对话（含向量数据） |
| `POST` | `/api/v1/conversations/{id}/summary` | 重新生成摘要 |
| `POST` | `/api/v1/search` | 语义搜索 |
| `POST` | `/api/v1/search/ask` | RAG问答 |

## 技术栈

- **后端**: Python 3.10+, FastAPI, SQLAlchemy (async), aiosqlite, ChromaDB, sentence-transformers, zhipuai
- **前端**: Vue 3, TypeScript, Vite, Pinia, Vue Router, Axios
- **安卓**: Kotlin, Retrofit 2, Coroutines, Material Design 3, ViewBinding
- **存储**: SQLite + ChromaDB（均为本地文件，零外部依赖）

## 项目结构

```
chatMem/
├── backend/                 # FastAPI 后端
│   ├── app/
│   │   ├── main.py          # 入口
│   │   ├── config.py         # 配置
│   │   ├── database.py       # 数据库引擎
│   │   ├── models/           # ORM 模型
│   │   ├── schemas/          # Pydantic 模型
│   │   ├── api/              # 路由
│   │   └── services/         # 业务逻辑（解析/embedding/向量/LLM/摘要）
│   ├── tests/
│   └── requirements.txt
├── frontend/                # Vue 3 前端
│   └── src/
│       ├── api/              # Axios 客户端
│       ├── views/            # 页面（列表/详情/搜索）
│       ├── components/       # 组件（聊天气泡/摘要卡片/搜索栏）
│       ├── stores/           # Pinia 状态管理
│       └── router/           # 路由
├── android/                 # Kotlin Android App
│   └── app/src/main/java/com/wechatmem/app/
│       ├── parser/           # 微信文本解析
│       ├── data/             # API客户端 + 数据模型
│       └── ui/               # 界面（接收/列表/详情/搜索）
└── .gitignore
```

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `WECHATMEM_ZHIPU_API_KEY` | 智谱AI API密钥 | 空（摘要/RAG功能需要） |
| `WECHATMEM_ZHIPU_MODEL` | GLM模型名 | `glm-4-flash` |
| `WECHATMEM_EMBEDDING_MODEL` | Embedding模型 | `BAAI/bge-small-zh-v1.5` |
| `WECHATMEM_DEBUG` | 调试模式 | `false` |

## 测试

```bash
cd backend
pytest tests/test_parser.py -v
```
