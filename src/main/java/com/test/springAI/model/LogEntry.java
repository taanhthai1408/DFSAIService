package com.test.springAI.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Common Log Model - Unified representation of a log entry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogEntry {
    private String id;
    
    @JsonProperty("timestamp")
    @JsonAlias("@timestamp")
    @JsonFormat(pattern = "[yyyy-MM-dd HH:mm:ss.SSS][yyyy-MM-dd HH:mm:ss][yyyy-MM-dd'T'HH:mm:ss.SSS'Z'][yyyy-MM-dd'T'HH:mm:ss.SSS]")
    private LocalDateTime timestamp;
    
    @JsonAlias({"level", "log_level", "severity"})
    private String level;    // INFO, ERROR, WARN, DEBUG
    private String source;   // System/Service name
    private String message;
    private String traceId;
    private String spanId;
    private Map<String, Object> metadata; // Extra fields like user_id, ip, etc.
    private String raw;      // Original raw log line
}
