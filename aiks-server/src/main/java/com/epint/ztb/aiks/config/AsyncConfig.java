package com.epint.ztb.aiks.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 文档摄取管道异步线程池
 */
@Configuration
public class AsyncConfig {

    @Bean("aiksIngestExecutor")
    public ThreadPoolTaskExecutor aiksIngestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("aiks-ingest-");
        // 队列满时由调用线程执行，避免上传请求被拒绝
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
