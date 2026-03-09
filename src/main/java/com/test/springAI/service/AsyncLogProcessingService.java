package com.test.springAI.service;

import com.test.springAI.model.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncLogProcessingService {

    private final AiNormalizationService aiNormalizationService;
    private final LogVectorService logVectorService;

    @Async("logProcessingExecutor")
    public void processLogsAsynchronously(List<String> rawLogLines, String source) {
        log.info("Starting background async processing for {} log messages from source: {}", rawLogLines.size(), source);

        // Increased batch size from 20 to 100 for better AI API throughput and bulk vector ingestion
        int batchSize = 100;
        int totalProcessed = 0;

        for (int i = 0; i < rawLogLines.size(); i += batchSize) {
            List<String> batch = rawLogLines.subList(i, Math.min(i + batchSize, rawLogLines.size()));
            try {
                // The AI Normalization Service will process this chunk
                List<LogEntry> normalizedEntries = aiNormalizationService.normalizeBatch(batch, source);
                
                if (normalizedEntries != null && !normalizedEntries.isEmpty()) {
                    // Bulk insert into the Vector Store
                    logVectorService.storeLogs(normalizedEntries);
                    totalProcessed += normalizedEntries.size();
                }
            } catch (Exception e) {
                log.error("Error processing AI batch from Filebeat in background: {}", e.getMessage(), e);
            }
        }
        
        log.info("Background async processing complete. Successfully vectorized {}/{} logs.", totalProcessed, rawLogLines.size());
    }
}
