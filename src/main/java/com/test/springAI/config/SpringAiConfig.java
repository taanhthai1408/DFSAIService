package com.test.springAI.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI Configuration
 * - ChatClient: Bean dùng cho các Service gọi API
 * - ChatMemory: Lưu lịch sử hội thoại in-memory (cửa sổ 10 messages)
 * - SimpleVectorStore: In-memory vector store dùng cho RAG
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

    /**
     * In-memory Vector Store dùng cho RAG demo.
     * Dùng EmbeddingModel của Ollama để tạo vector.
     * Production nên thay bằng PgVector, Chroma, Weaviate...
     */
    @Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
