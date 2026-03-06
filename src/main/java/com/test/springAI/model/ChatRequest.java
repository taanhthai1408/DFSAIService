package com.test.springAI.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body cho chat API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /** Nội dung tin nhắn của user */
    private String message;

    /** ID hội thoại - dùng cho chat có memory (để trống = chat không có history) */
    private String conversationId;

    /** System prompt - hướng dẫn hành vi của AI */
    private String systemPrompt;

    /** Temperature (0.0 - 1.0). Null = dùng config mặc định */
    private Double temperature;

    // [PgVector] fields cho /chat/pgvector endpoint
    /** Số documents context tối đa lấy từ PgVector (mặc định: 3) */
    private Integer topK;

    /** Ngưỡng similarity tối thiểu (0.0 - 1.0, mặc định: 0.0) */
    private Double threshold;
}
