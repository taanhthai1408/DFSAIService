package com.test.springAI.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Request body cho RAG API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagRequest {

    /** Danh sách documents để nạp vào Vector Store */
    private List<String> documents;

    /** Câu hỏi cần trả lời dựa trên documents */
    private String question;

    /** ID hội thoại (tuỳ chọn, cho RAG + memory) */
    private String conversationId;

    // [PgVector] fields cho các endpoint nâng cấp
    /** Số kết quả tối đa lấy từ PgVector (mặc định: 5) */
    private Integer topK;

    /** Biểu thức lọc metadata PgVector, ví dụ: "source == 'input.txt'" */
    private String filter;
}
