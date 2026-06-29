package com.documind.query.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Worker pool that drives each SSE response off the request thread: retrieval +
 * the streamed LLM call run here while the controller returns the SseEmitter
 * immediately.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "sseExecutor")
    public Executor sseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sse-");
        executor.initialize();
        return executor;
    }
}
