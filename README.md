# WeChatMem - 微信聊天记忆系统

微信聊天记录的语义搜索与智能问答平台。支持云端和本地两种存储模式。

## 系统架构

```
                          ┌─────────────────────────────────────┐
                          │           Android App               │
                          │  ┌───────────┐  ┌───────────────┐  │
[微信] ──分享──>          │  │ 云端模式  │  │  本地模式     │  │
                          │  │ Retrofit  │  │ Room+ONNX+LLM │  │
                          │  └─────┬─────┘  └───────┬───────┘  │
                          │        │    Repository   │          │
                          │        │    Pattern      │          │
                          └────────┼─────────────────┼──────────┘
                                   │                 │
                    ┌──────────────▼──────┐          │ 智谱GLM API
                    │   FastAPI 后端      │          │ (摘要/问答)
                    │  ├─ SQLite          │          ▼
                    │  ├─ ChromaDB        │   open.bigmodel.cn
                    │  ├─ BGE Embedding   │
                    │  └─ 智谱GLM         │
                    └─────────┬───────────┘
                              │
                    ┌─────────▼───────────┐
                    │  Vue 3 Web 前端     │
                    │  (管理/浏览/搜索)    │
                    └─────────────────────┘
```

## 功能特性

- **聊天记录导入**: 微信分享 → App 自动解析，或手动粘贴导入
- **语义搜索**: BGE-small-zh 向量化 + 余弦相似度检索
- **AI 问答**: RAG 模式，基于聊天记录上下文回答问题
- **智能摘要**: 智谱 GLM 自动生成对话摘要
- **双存储模式**: 云端（服务器）或本地（手机端）自由切换
- **数据迁移**: 本地 → 云端一键迁移
- **用户认证**: JWT Token 体系，多用户数据隔离
- **Docker 部署**: 一键 docker-compose 启动

## 快速开始

### 后端

```bash
cd backend
pip install -r requirements.txt
echo "WECHATMEM_ZHIPU_API_KEY=your_key" > .env
uvicorn app.main:app --reload
```

API: `http://localhost:8000`，文档: `http://localhost:8000/docs`

### Web 前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:3000`

### Android App

Android Studio 打开 `android/` 目录，Gradle 同步后构建运行。

- 默认本地模式，无需服务器，首次启动直接进入主界面
- 智谱 API Key 在「我的 → AI 配置」中填写（问答/摘要功能需要）
- 编译产物自动命名为 `wechatmem-{versionName}-{buildType}.apk`

### Docker 一键部署

```bash
docker-compose up -d
```

## 存储模式

| 特性 | 云端模式 | 本地模式 |
|------|----------|----------|
| 数据存储 | 服务器 SQLite + ChromaDB | 手机 Room (SQLite) |
| 向量化 | 服务端 BGE Embedding | ONNX Runtime Mobile |
| 搜索 | ChromaDB 向量检索 | SQLite BLOB + 暴力余弦 |
| 摘要/问答 | 经后端调用智谱 GLM | App 直连智谱 GLM API |
| 需要登录 | 是 | 否 |
| 需要服务器 | 是 | 否 |

切换方式：App「我的 → 存储模式」开关（默认本地模式，无需登录）

## API 接口

### 认证 (无需 Token)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/register` | 注册 |
| POST | `/api/v1/auth/login` | 登录 |
| POST | `/api/v1/auth/refresh` | 刷新 Token |
| GET | `/api/v1/auth/me` | 当前用户信息 |

### 对话 (需要 Bearer Token)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/conversations` | 上传对话 |
| POST | `/api/v1/conversations/upload` | 上传对话 (含图片) |
| GET | `/api/v1/conversations` | 对话列表 (分页) |
| GET | `/api/v1/conversations/{id}` | 对话详情 |
| PATCH | `/api/v1/conversations/{id}` | 更新标题 |
| DELETE | `/api/v1/conversations/{id}` | 删除对话 |
| POST | `/api/v1/conversations/{id}/summary` | 生成摘要 |

### 搜索 (需要 Bearer Token)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/search` | 语义搜索 |
| POST | `/api/v1/search/ask` | RAG 问答 |

### 其他

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/health` | 健康检查 |

## 技术栈

- **后端**: Python 3.11, FastAPI, SQLAlchemy async, aiosqlite, ChromaDB, sentence-transformers (BGE-small-zh-v1.5), zhipuai, slowapi
- **前端**: Vue 3, TypeScript, Vite, Pinia, Vue Router, Axios
- **Android**: Kotlin, Retrofit 2, OkHttp, Room, ONNX Runtime, Coroutines, Material Design 3
- **部署**: Docker, docker-compose, Nginx

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `WECHATMEM_ZHIPU_API_KEY` | 智谱 AI API 密钥 | 空 |
| `WECHATMEM_ZHIPU_MODEL` | GLM 模型名 | `glm-4-flash` |
| `WECHATMEM_EMBEDDING_MODEL` | Embedding 模型 | `BAAI/bge-small-zh-v1.5` |
| `WECHATMEM_JWT_SECRET` | JWT 签名密钥 | 随机生成 |
| `WECHATMEM_CORS_ORIGINS` | CORS 允许源 | `*` |
| `WECHATMEM_DEBUG` | 调试模式 | `false` |

## 测试

```bash
cd backend
pytest -v  # 31 tests: parser(7) + auth(8) + conversations(10) + upload(3) + search(4)
```

## 开发历程

| 阶段 | 内容 |
|------|------|
| Phase 1-3 | 核心功能：解析、存储、向量化、搜索、问答、摘要 |
| Phase 4 | JWT 认证体系，多用户数据隔离 |
| Phase 5 | 前端/Android 登录注册，Docker 部署 |
| Phase 6 | 生产加固：限流、日志、上传校验、Toast、骨架屏、Markdown 导出 |
| Phase 7 | Android 本地存储模式：Room + ONNX + 本地向量搜索 + LLM 直连 |
| Phase 8 | Android 导航重构：底部导航栏 (对话/搜索/我的)，默认本地模式，LLM 模型可选，Embedding 模型内置 assets |
| Phase 9 | 搜索/问答合并（自动 RAG，有网显示 AI 回答），自定义 App 图标，配置页分区，APK 自动命名 |
