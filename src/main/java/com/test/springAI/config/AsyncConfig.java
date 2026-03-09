package com.test.springAI.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "logProcessingExecutor")
    public Executor logProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Number of core threads (matches typical CPU cores)
        executor.setCorePoolSize(4);
        // Maximum number of threads in the pool
        executor.setMaxPoolSize(10);
        // Queue capacity before new threads are created or rejection happens
        executor.setQueueCapacity(500);
        // Prefix for thread names to easily identify them in logs
        executor.setThreadNamePrefix("LogProcessor-");
        
        // When the queue (500) and max threads (10) are full, 
        // the CallerRunsPolicy forces the incoming thread (Tomcat HTTP request) 
        // to execute the task. This gracefully throttles Filebeat instead of throwing an exception.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        return executor;
    }
}
