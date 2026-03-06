package com.test.springAI.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI Configuration
 * - ChatClient: Bean dùng cho các Service gọi API
 * - ChatMemory: Lưu lịch sử hội thoại in-memory (cửa sổ 10 messages)
 * - VectorStore: PgVectorStore được auto-configure từ application.yaml
 * (spring.ai.vectorstore.pgvector.*)
 */
@Configuration
public class SpringAiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * Repository lưu trữ conversation history trong memory.
     * Có thể thay bằng Redis/PostgreSQL repository trong production.
     */
    @Bean
    public InMemoryChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    /**
     * ChatMemory với cửa sổ 10 messages gần nhất.
     * Khi vượt quá 10 messages, messages cũ nhất bị xóa.
     */
    @Bean
    public ChatMemory chatMemory(InMemoryChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)
                .build();
    }

    // NOTE: VectorStore (PgVectorStore) được Spring AI auto-configure
    // dựa trên cấu hình spring.ai.vectorstore.pgvector.* trong application.yaml
    // và dependency spring-ai-starter-vector-store-pgvector trong pom.xml.
    // Không cần khai báo Bean thủ công.
}
