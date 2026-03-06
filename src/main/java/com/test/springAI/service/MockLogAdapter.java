package com.test.springAI.service;

import com.test.springAI.model.LogCriteria;
import com.test.springAI.model.LogEntry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mock implementation of LogSourceProvider for demo purposes.
 * Simulates different systems by their source name.
 */
@Service
public class MockLogAdapter implements LogSourceProvider {

    @Override
    public String getName() {
        return "Mock Systems Adapter";
    }

    @Override
    public boolean supports(String source) {
        // Supports any source for demo, but typically would check against a list or pattern
        return source != null && (source.startsWith("system-") || source.startsWith("service-"));
    }

    @Override
    public List<LogEntry> fetchLogs(LogCriteria criteria) {
        List<LogEntry> logs = new ArrayList<>();
        int count = criteria.getLimit();
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 0; i < count; i++) {
            logs.add(generateLog(criteria.getSource(), now.minusMinutes(i)));
        }
        return logs;
    }

    @Override
    public Flux<LogEntry> streamLogs(String source) {
        return Flux.interval(Duration.ofSeconds(2))
                .map(i -> generateLog(source, LocalDateTime.now()));
    }

    private LogEntry generateLog(String source, LocalDateTime ts) {
        String level = Math.random() > 0.8 ? "ERROR" : (Math.random() > 0.6 ? "WARN" : "INFO");
        String message = getMockMessage(level, source);
        
        return LogEntry.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(ts)
                .level(level)
                .source(source)
                .message(message)
                .traceId(UUID.randomUUID().toString().substring(0, 8))
                .metadata(Map.of("host", "server-01", "app", source))
                .raw(String.format("[%s] %s %s: %s", ts, level, source, message))
                .build();
    }

    private String getMockMessage(String level, String source) {
        if ("ERROR".equals(level)) {
            return "NullPointerException at com.example." + source + ".MainProcessor.process(MainProcessor.java:42)";
        } else if ("WARN".equals(level)) {
            return "Memory usage high: 85% on node-1";
        }
        return "Started processing request GET /api/v1/data";
    }
}
