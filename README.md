# dzjy-aiks — AI 智能问答（法规咨询）服务

面向招标代理机构的法规咨询智能问答服务：法规文档上传 → 条款级分块向量化（RAG）→ 检索增强问答，回答强制引用具体法规条款出处。

## 技术栈

| 项 | 版本/选择 |
|---|---|
| JDK | 17（**独立于平台 Java 8 编译链，勿将本工程加入根 pom modules**） |
| Spring Boot | 3.3.12 |
| Spring AI | 1.0.0（spring-ai-bom；OpenAI 兼容协议手工装配双模型） |
| 向量库 | SimpleVectorStore + JSON 文件持久化（`aiks.vectorstore.type` 预留 redis/milvus） |
| ORM | MyBatis-Plus 3.5.7（独立 MySQL 库 `aiks`） |
| 文档解析 | Apache Tika（pdf/doc/docx/txt） |

## 部署步骤

1. **JDK 17 运行时**（服务器需单独安装，与平台 JDK 8 共存即可）。
2. **建库**：
   ```sql
   create database aiks default character set utf8mb4;
   -- 然后在 aiks 库执行 src/main/resources/schema-aiks.sql
   ```
3. **配置环境变量**（云端大模型 API 密钥一律走环境变量，禁止写入代码/配置库）：
   | 变量 | 说明 | 示例 |
   |---|---|---|
   | AIKS_CHAT_BASEURL | 对话模型 OpenAI 兼容地址 | `https://api.deepseek.com`（通义 `https://dashscope.aliyuncs.com/compatible-mode`，智谱 `https://open.bigmodel.cn/api/paas/v4`） |
   | AIKS_CHAT_APIKEY | 对话模型 API Key | — |
   | AIKS_CHAT_MODEL | 对话模型名 | `deepseek-chat` |
   | AIKS_EMBED_BASEURL | 向量模型地址（可与 chat 不同厂商） | `https://dashscope.aliyuncs.com/compatible-mode` |
   | AIKS_EMBED_APIKEY | 向量模型 API Key | — |
   | AIKS_EMBED_MODEL | 向量模型名 | `text-embedding-v3` |
   | AIKS_SERVER_APIKEY | 平台侧调用本服务的静态令牌（请求头 X-Api-Key） | 自定义强随机串 |
   | AIKS_DB_HOST / AIKS_DB_PORT / AIKS_DB_USER / AIKS_DB_PASSWORD | aiks 库连接 | — |
4. **启动**：
   ```bash
   mvn spring-boot:run -pl aiks-server
   # 或打包后：java -jar aiks-server/target/aiks-server-1.0.0-SNAPSHOT.jar
   ```
   默认端口 `9093`，健康检查 `GET /actuator/health`。
5. **出网**：防火墙需放行到 chat/embedding 两个 base-url 域名的 HTTPS 出网。

## 快速验证

```bash
# 上传法规文档（multipart，异步摄取）
curl -X POST http://localhost:9093/api/v1/documents/upload \
  -H "X-Api-Key: $AIKS_SERVER_APIKEY" \
  -F "file=@政府采购法.docx" -F "lawName=中华人民共和国政府采购法" -F "lawLevel=法律"

# 轮询状态直到 READY
curl -H "X-Api-Key: $AIKS_SERVER_APIKEY" http://localhost:9093/api/v1/documents?status=READY

# 问答（首次不传 sessionId，多轮携带返回的 sessionId）
curl -X POST http://localhost:9093/api/v1/chat \
  -H "X-Api-Key: $AIKS_SERVER_APIKEY" -H "Content-Type: application/json" \
  -d '{"question":"公开招标数额标准以上的货物项目评标专家如何抽取？"}'
```

回答中的引用以 `〔n〕` 标注，`references[]` 中携带对应的法规名、条款号、章名、片段原文与相似度分数。

## REST API 一览

统一返回 `{code, msg, traceId, data}`；`code=0` 成功，错误码见 `AiksErrorCode`（AIKS_ 前缀）。

| 方法+路径 | 用途 |
|---|---|
| POST /api/v1/chat | 问答（非流式，LLM 响应通常 5~60s，调用方超时需 ≥180s） |
| GET /api/v1/sessions/{id}/messages | 会话历史（分页） |
| DELETE /api/v1/sessions/{id} | 删除会话 |
| POST /api/v1/documents/upload | 上传法规文档（multipart，≤20MB，pdf/doc/docx/txt） |
| GET /api/v1/documents | 文档分页列表（keyword/status 过滤） |
| GET /api/v1/documents/{docGuid} | 详情 + 分块摘要 |
| DELETE /api/v1/documents/{docGuid} | 删除文档 |
| POST /api/v1/documents/{docGuid}/revectorize | 重新摄取（换 embedding 模型后需逐文档重建） |
| GET /actuator/health | 健康检查 |

## 目录说明

```
aiks-server/src/main/java/com/epint/ztb/aiks/
├── common/       ApiResult / AiksErrorCode / AiksException / GlobalExceptionHandler / TraceIdFilter
├── config/       AiksProperties(yml映射) / AiModelConfig(双模型装配) / VectorStoreConfig / AsyncConfig / WebConfig
├── security/     ApiKeyAuthFilter(X-Api-Key 静态令牌)
├── controller/   ChatController / DocumentController
├── docinput/     TikaTextExtractor / RegulationChunker(条款结构分块) / DocumentIngestService(异步状态机)
│                 / DocumentService / VectorStoreManager(文件持久化与写锁)
├── rag/          RagChatService / RetrievalService / RagPromptBuilder / CitationResolver
├── chatmemory/   MysqlChatMemoryRepository(会话历史落库)
├── entity|mapper|dto
└── resources/
    ├── application.yml        全部可配置项（含注释）
    ├── schema-aiks.sql        aiks 库建表脚本（7 张表）
    └── prompts/             rag-system.st 系统提示词（引用规范/拒答规范/防注入）、rag-fallback.st 兜底提示词（寒暄引导/未命中提示）
```

## 数据与边界说明

- **向量可重建**：`aiks_doc_chunk` 是权威数据源，向量本体只在向量库文件中；revectorize 接口可按文档重建。**更换 embedding 模型后必须全量重嵌入**（维度与语义空间不同）。
- **扫描件不支持**：上传文档提取文本 < 500 字符（默认）时置 FAILED，OCR 为二期项。
- **拒答兜底**：检索 0 命中时走大模型兜底（`rag-fallback.st`）——寒暄/无关问题礼貌引导，法规问题未命中给提示话术；模型调用失败降级为固定话术（`fallback=true` 均表示无检索命中、无引用）。
- **会话历史**：窗口默认 20 条（`aiks.rag.history-max-messages`），全量消息留痕在 `aiks_chat_message`。
- **已知暂缓项**（见《AI法规问答模块实施计划.md》）：限流、敏感词过滤、token 用量落库统计、运行时配置接口、平台侧对接（ztb-aiks-impl/sdk）、流式输出、网页 UI。

## 服务器资源建议

2C4G 起步；磁盘：原始文件 + 向量库文件（5 万 chunk × 1024 维 ≈ 200MB 级）。生产建议配置 systemd 或 Docker 守护。
