package com.test.springAI.service;

import com.test.springAI.model.LogCriteria;
import com.test.springAI.model.LogEntry;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Interface for pluggable log sources
 */
public interface LogSourceProvider {
    
    /**
     * Get the descriptive name of the log source
     */
    String getName();

    /**
     * Check if this provider supports a specific source string
     */
    boolean supports(String source);

    /**
     * Fetch a history of logs based on criteria
     */
    List<LogEntry> fetchLogs(LogCriteria criteria);

    /**
     * Stream logs in real-time (Reactive)
     */
    Flux<LogEntry> streamLogs(String source);
}
