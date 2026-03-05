package com.test.springAI.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body cho chat API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** Câu trả lời từ AI */
    private String answer;

    /** ID hội thoại (nếu có memory) */
    private String conversationId;

    /** Model đang dùng */
    private String model;
}
