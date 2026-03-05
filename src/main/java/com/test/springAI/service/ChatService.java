package com.test.springAI.service;

import com.test.springAI.model.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * =============================================================
 * ChatService - Tất cả tính năng chat với Spring AI
 * =============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

        private final ChatClient chatClient;
        private final ChatModel chatModel;
        private final ChatMemory chatMemory;

        // =========================================================
        // 1. SIMPLE CHAT
        // =========================================================

        /**
         * Chat đơn giản nhất với llama3.
         */
        public String simpleChat(String message) {
                log.debug("SimpleChat: {}", message);
                return chatClient.prompt()
                                .user(message)
                                .call()
                                .content();
        }

        // =========================================================
        // 2. CHAT VỚI SYSTEM PROMPT
        // =========================================================

        /**
         * Chat với system prompt để định hình hành vi AI.
         */
        public String chatWithSystem(String systemPrompt, String userMessage) {
                log.debug("ChatWithSystem - system: {}, user: {}", systemPrompt, userMessage);
                return chatClient.prompt()
                                .system(systemPrompt)
                                .user(userMessage)
                                .call()
                                .content();
        }

        // =========================================================
        // 3. STREAMING CHAT
        // =========================================================

        /**
         * Streaming chat: nhận response từng token real-time.
         */
        public Flux<String> streamChat(String message) {
                log.debug("StreamChat: {}", message);
                return chatClient.prompt()
                                .user(message)
                                .stream()
                                .content();
        }

        /**
         * Streaming chat với system prompt.
         */
        public Flux<String> streamChatWithSystem(String systemPrompt, String userMessage) {
                return chatClient.prompt()
                                .system(systemPrompt)
                                .user(userMessage)
                                .stream()
                                .content();
        }

        // =========================================================
        // 4. CHAT VỚI MEMORY
        // =========================================================

        /**
         * Chat có conversation memory: AI nhớ lịch sử hội thoại.
         * Spring AI 1.0.0: MessageChatMemoryAdvisor dùng builder pattern.
         */
        public String chatWithMemory(String conversationId, String message) {
                log.debug("ChatWithMemory [{}]: {}", conversationId, message);
                return chatClient.prompt()
                                .user(message)
                                .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                                                .conversationId(conversationId)
                                                .build())
                                .call()
                                .content();
        }

        /**
         * Chat với memory + system prompt.
         */
        public String chatWithMemoryAndSystem(String conversationId, String systemPrompt, String message) {
                return chatClient.prompt()
                                .system(systemPrompt)
                                .user(message)
                                .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                                                .conversationId(conversationId)
                                                .build())
                                .call()
                                .content();
        }

        // =========================================================
        // 5. STRUCTURED OUTPUT
        // =========================================================

        /**
         * Yêu cầu AI trả về JSON → deserialized thành Java object.
         */
        public <T> T structuredOutput(String message, Class<T> type) {
                log.debug("StructuredOutput [{}]: {}", type.getSimpleName(), message);
                return chatClient.prompt()
                                .user(message)
                                .call()
                                .entity(type);
        }

        // =========================================================
        // 6. CHAT VỚI CUSTOM OPTIONS
        // =========================================================

        /**
         * Chat với OllamaOptions tuỳ chỉnh (temperature, maxTokens...).
         */
        public String chatWithOptions(String message, double temperature, int maxTokens) {
                log.debug("ChatWithOptions: temp={}, maxTokens={}", temperature, maxTokens);
                Prompt prompt = new Prompt(
                                List.of(new UserMessage(message)),
                                OllamaOptions.builder()
                                                .temperature(temperature)
                                                .numPredict(maxTokens)
                                                .build());
                return chatModel.call(prompt).getResult().getOutput().getText();
        }

        /**
         * Chat với nhiều messages (system + user).
         */
        public String chatWithMessages(String systemInstruction, String userMessage) {
                Prompt prompt = new Prompt(List.of(
                                new SystemMessage(systemInstruction),
                                new UserMessage(userMessage)));
                return chatModel.call(prompt).getResult().getOutput().getText();
        }

        // =========================================================
        // Helper
        // =========================================================

        public ChatResponse buildResponse(String conversationId, String answer) {
                return ChatResponse.builder()
                                .answer(answer)
                                .conversationId(conversationId)
                                .model("llama3")
                                .build();
        }
}
