package com.test.springAI.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Filter criteria for fetching logs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogCriteria {
    private String source;
    private String level;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String keyword;
    @Builder.Default
    private int limit = 100;
}
