package com.test.springAI.service;

import com.test.springAI.model.LogCriteria;
import com.test.springAI.model.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrator for multiple log sources.
 * Manages a list of LogSourceProviders and routes requests correctly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogIntegrationService {

    private final List<LogSourceProvider> providers;

    /**
     * Fetch logs from the appropriate provider
     */
    public List<LogEntry> getLogs(LogCriteria criteria) {
        String source = criteria.getSource();
        Optional<LogSourceProvider> provider = findProvider(source);
        
        if (provider.isPresent()) {
            log.info("Fetching logs from provider: {} for source: {}", 
                     provider.get().getName(), source);
            return provider.get().fetchLogs(criteria);
        } else {
            log.warn("No log provider found for source: {}", source);
            return Collections.emptyList();
        }
    }

    /**
     * Stream logs from the appropriate provider
     */
    public Flux<LogEntry> streamLogs(String source) {
        return findProvider(source)
                .map(p -> p.streamLogs(source))
                .orElseGet(() -> {
                    log.warn("No stream provider found for source: {}", source);
                    return Flux.empty();
                });
    }

    /**
     * Get all supported sources from all providers (simulated)
     */
    public List<String> getAvailableSources() {
        // In a real app, this might query a discovery service or config
        return List.of("system-payment", "system-auth", "service-inventory", "service-shipping");
    }

    private Optional<LogSourceProvider> findProvider(String source) {
        return providers.stream()
                .filter(p -> p.supports(source))
                .findFirst();
    }
}
