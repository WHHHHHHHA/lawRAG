package com.epint.ztb.aiks.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AIKS 服务配置项（前缀 aiks.*）
 *
 * 云端 API 密钥一律通过环境变量注入，禁止写死在配置文件或入库。
 */
@Data
@ConfigurationProperties(prefix = "aiks")
public class AiksProperties {

    private ChatProps chat = new ChatProps();
    private EmbeddingProps embedding = new EmbeddingProps();
    private VectorStoreProps vectorstore = new VectorStoreProps();
    private RagProps rag = new RagProps();
    private SecurityProps security = new SecurityProps();
    private IngestProps ingest = new IngestProps();

    /** 对话模型（OpenAI 兼容端点） */
    @Data
    public static class ChatProps {
        private String baseUrl;
        private String apiKey;
        private String model;
        private Double temperature = 0.1;
        private Integer maxTokens = 2048;
        /** 大模型调用读超时（毫秒） */
        private Integer timeoutMs = 120000;
    }

    /** 向量模型（可与 chat 指向不同厂商） */
    @Data
    public static class EmbeddingProps {
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer dimensions = 1024;
        private Integer timeoutMs = 60000;
        /** 单次 embedding 请求文档数 */
        private Integer batchSize = 10;
    }

    @Data
    public static class VectorStoreProps {
        /** simple（第一期）/ redis / milvus（预留） */
        private String type = "simple";
        /** SimpleVectorStore 的 JSON 持久化文件路径 */
        private String simpleFile = "data/aiks-vector.json";
    }

    @Data
    public static class RagProps {
        private Integer topK = 6;
        private Double similarityThreshold = 0.55;
        /** 会话历史窗口消息数 */
        private Integer historyMaxMessages = 20;
    }

    @Data
    public static class SecurityProps {
        /** 平台侧调用的静态令牌 */
        private String apiKey;
        private Integer rateLimitPerUserPerMin = 6;
        private Integer globalQpm = 60;
        private Boolean sensitiveCheckEnabled = true;
    }

    @Data
    public static class IngestProps {
        /** 提取文本少于此字符数判定为扫描件/空文件 */
        private Integer minTextChars = 500;
        /** 单条法规条款超过此 token 估算值时二次切分 */
        private Integer articleMaxTokens = 800;
        /** 非法规结构文本兜底分块大小（token） */
        private Integer fallbackChunkSize = 1000;
        /** 小于此 token 估算值的碎块并入相邻块 */
        private Integer minChunkTokens = 50;
    }
}
