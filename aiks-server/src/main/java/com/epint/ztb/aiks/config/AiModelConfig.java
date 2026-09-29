package com.epint.ztb.aiks.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 大模型装配：手工构造两个 OpenAiApi，chat 与 embedding 可分别指向不同厂商
 * （例如 chat=DeepSeek、embedding=DashScope text-embedding-v3，均为 OpenAI 兼容协议）。
 *
 * 不使用 spring-ai-starter-model-openai 自动配置——自动配置只有一个 base-url，
 */
//无法满足双端点分别配置的要求。切换厂商只需修改 aiks.chat.*/aiks.embedding./* 配置。

@Configuration
public class AiModelConfig {

    /**
     * 对话模型
     */
    @Bean
    public OpenAiChatModel chatModel(AiksProperties props) {
        AiksProperties.ChatProps chat = props.getChat();
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(chat.getBaseUrl())
                .apiKey(chat.getApiKey())
                .restClientBuilder(restClientBuilder(chat.getTimeoutMs()))
                .webClientBuilder(WebClient.builder())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(chat.getModel())
                        .temperature(chat.getTemperature())
                        .maxTokens(chat.getMaxTokens())
                        .build())
                .build();
    }

    /**
     * 向量模型
     */
    @Bean
    public EmbeddingModel embeddingModel(AiksProperties props) {
        AiksProperties.EmbeddingProps embed = props.getEmbedding();
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(embed.getBaseUrl())
                .apiKey(embed.getApiKey())
                .restClientBuilder(restClientBuilder(embed.getTimeoutMs()))
                .webClientBuilder(WebClient.builder())
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(embed.getModel())
                        .dimensions(embed.getDimensions())
                        .build());
    }

    /**
     * 指定读/连接超时的 RestClient.Builder（LLM 响应慢，默认超时不够）
     */
    private RestClient.Builder restClientBuilder(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(timeoutMs);
        return RestClient.builder().requestFactory(factory);
    }
}
