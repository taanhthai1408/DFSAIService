package com.test.springAI.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Request body cho embedding API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingRequest {

    /** Single text để embed */
    private String text;

    /** Danh sách texts để embed cùng lúc */
    private List<String> texts;
}
