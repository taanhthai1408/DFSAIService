package com.test.springAI.service;

import com.test.springAI.model.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service to manage storing and searching logs in the Vector Database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogVectorService {

    private final SimpleVectorStore vectorStore;

    /**
     * Store a list of LogEntry objects in the vector store
     */
    public void storeLogs(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            log.warn("No log entries to store in Vector Store");
            return;
        }
        log.info("Storing {} log entries into Vector Store", entries.size());
        
        List<Document> documents = entries.stream()
                .map(this::toDocument)
                .collect(Collectors.toList());
        
        vectorStore.add(documents);
    }

    /**
     * Convert LogEntry to Spring AI Document
     */
    private Document toDocument(LogEntry entry) {
        // Content used for embedding and similarity search
        String content = String.format("[%s] [%s] %s: %s", 
                entry.getTimestamp(), entry.getLevel(), entry.getSource(), entry.getMessage());
        log.info("Converting LogEntry to Document: {}", content);
        
        // Metadata for filtering
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", entry.getSource());
        metadata.put("level", entry.getLevel());
        metadata.put("id", entry.getId());
        if (entry.getTimestamp() != null) {
            metadata.put("timestamp", entry.getTimestamp().toString());
        }
        if (entry.getMetadata() != null) {
            metadata.putAll(entry.getMetadata());
        }
        
        // The LogEntry should already have an ID set by storeLogs, but as a fallback
        String entryId = (entry.getId() == null || entry.getId().trim().isEmpty()) 
                ? UUID.randomUUID().toString() 
                : entry.getId();
                
        return new Document(entryId, content, metadata);
    }
}
