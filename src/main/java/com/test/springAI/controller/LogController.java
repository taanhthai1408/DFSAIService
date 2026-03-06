package com.test.springAI.controller;

import com.test.springAI.model.LogCriteria;
import com.test.springAI.model.LogEntry;
import com.test.springAI.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * REST API for Multi-System Log Integration & AI Analysis
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
public class LogController {

    private final LogIntegrationService logIntegrationService;
    private final AiLogProcessor aiLogProcessor;
    private final FileLogAdapter fileLogAdapter;
    private final AiNormalizationService aiNormalizationService;
    private final LogVectorService logVectorService;
    private final RagService ragService;

    /** GET /api/logs/sources - Get all available log sources */
    @GetMapping("/sources")
    public ResponseEntity<List<String>> getSources() {
        return ResponseEntity.ok(logIntegrationService.getAvailableSources());
    }

    /** GET /api/logs?source=... - Fetch raw logs */
    @GetMapping
    public ResponseEntity<List<LogEntry>> getLogs(
            @RequestParam String source,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(logIntegrationService.getLogs(
                LogCriteria.builder().source(source).limit(limit).build()
        ));
    }

    /** GET /api/logs/stream?source=... - Stream logs real-time */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public Flux<LogEntry> streamLogs(@RequestParam String source) {
        return logIntegrationService.streamLogs(source);
    }

    /** POST /api/logs/ai/summarize - Summarize logs with AI */
    @PostMapping("/ai/summarize")
    public ResponseEntity<Map<String, String>> summarize(@RequestBody Map<String, Object> body) {
        String source = (String) body.get("source");
        int limit = body.containsKey("limit") ? (int) body.get("limit") : 10;
        String summary = aiLogProcessor.summarizeLogs(source, limit);
        return ResponseEntity.ok(Map.of("summary", summary));
    }

    /** POST /api/logs/ai/rca - Root Cause Analysis with AI */
    @PostMapping("/ai/rca")
    public ResponseEntity<Map<String, String>> analyze(@RequestBody Map<String, String> body) {
        String source = body.get("source");
        String analysis = aiLogProcessor.performRCA(source);
        return ResponseEntity.ok(Map.of("analysis", analysis));
    }

    /** POST /api/logs/ingest/file - Read file, normalize, and store in vector DB */
    @PostMapping("/ingest/file")
    public ResponseEntity<Map<String, Object>> ingestFile(@RequestBody Map<String, Object> body) {
        String filePath = (String) body.get("filePath");
        Integer requestedLimit = body.containsKey("limit") ? (Integer) body.get("limit") : 0;
        String filterKeyword = (String) body.get("keyword");
        String filterLevel = (String) body.get("level");
        boolean isFullFile = (requestedLimit == null || requestedLimit <= 0);
        
        try {
            int totalRawLinesRead = 0;
            int actuallyProcessed = 0;
            int batchSize = 20;
            List<String> currentBatch = new java.util.ArrayList<>();
            
            log.info("Starting ADVANCED streaming ingestion for file: {}. Filter: [keyword={}, level={}]", 
                    filePath, filterKeyword, filterLevel);
            
            try (java.util.stream.Stream<String> lineStream = fileLogAdapter.getLinesStream(filePath)) {
                java.util.stream.Stream<String> targetStream = isFullFile ? lineStream : lineStream.limit(requestedLimit);
                
                StringBuilder multilineBuffer = new StringBuilder();
                Iterable<String> iterable = targetStream::iterator;
                
                for (String line : iterable) {
                    totalRawLinesRead++;
                    
                    // Basic multiline detection: if line starts with whitespace or doesn't look like a new log timestamp
                    // we assume it's a continuation (like a stack trace).
                    // Simple heuristic: starts with ' ' (space), '\t' (tab), or doesn't match common timestamp start.
                    if (multilineBuffer.length() > 0 && (line.startsWith(" ") || line.startsWith("\t") || line.startsWith("at "))) {
                        multilineBuffer.append("\n").append(line);
                        continue;
                    }
                    
                    // If buffer has something, it means the PREVIOUS logical log is complete.
                    if (multilineBuffer.length() > 0) {
                        processLogicalLog(multilineBuffer.toString(), filterKeyword, filterLevel, currentBatch, filePath);
                        multilineBuffer.setLength(0);
                    }
                    
                    multilineBuffer.append(line);
                    
                    if (currentBatch.size() >= batchSize) {
                        actuallyProcessed += flushBatch(currentBatch, filePath);
                    }
                }
                
                // Final flush of buffer and batch
                if (multilineBuffer.length() > 0) {
                    processLogicalLog(multilineBuffer.toString(), filterKeyword, filterLevel, currentBatch, filePath);
                }
                if (!currentBatch.isEmpty()) {
                    actuallyProcessed += flushBatch(currentBatch, filePath);
                }
            }
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "file", filePath,
                    "totalRawRead", totalRawLinesRead,
                    "actuallyStored", actuallyProcessed,
                    "message", String.format("Successfully processed %d logical entries from %d raw lines (Filters active: %b)", 
                            actuallyProcessed, totalRawLinesRead, (filterKeyword != null || filterLevel != null))
            ));
        } catch (Exception e) {
            log.error("Error during advanced streaming ingestion for file {}: {}", filePath, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "error", e.getMessage()
            ));
        }
    }

    private void processLogicalLog(String logMsg, String keyword, String level, List<String> batch, String source) {
        // Apply level filter (case insensitive)
        if (level != null && !logMsg.toUpperCase().contains(level.toUpperCase())) {
            return;
        }
        // Apply keyword filter
        if (keyword != null && !logMsg.toLowerCase().contains(keyword.toLowerCase())) {
            return;
        }
        batch.add(logMsg);
    }

    private int flushBatch(List<String> batch, String filePath) {
        log.info("Processing logical batch of {} entries for file: {}", batch.size(), filePath);
        List<LogEntry> normalizedBatch = aiNormalizationService.normalizeBatch(batch, filePath);
        int count = 0;
        if (normalizedBatch != null && !normalizedBatch.isEmpty()) {
            logVectorService.storeLogs(normalizedBatch);
            count = normalizedBatch.size();
        }
        batch.clear();
        return count;
    }

    /** POST /api/logs/ai/ask - Ask questions against the vector store */
    @PostMapping("/ai/ask")
    public ResponseEntity<Map<String, String>> askLogs(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        log.info("AI Q&A with Vector Store: {}", question);
        
        String answer = ragService.ragWithAdvisor(question, 5); // Retrieve top 5 relevant logs
        return ResponseEntity.ok(Map.of("answer", answer));
    }
}
