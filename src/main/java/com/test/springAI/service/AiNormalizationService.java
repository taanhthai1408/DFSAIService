package com.test.springAI.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.test.springAI.model.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service that uses AI to normalize raw log lines into structured LogEntry objects.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiNormalizationService {

    private final ChatClient chatClient;

    /**
     * Normalize a single raw log line
     */
    public LogEntry normalize(String rawLine, String source) {
        log.debug("AI Normalizing single log line from source: {}", source);
        
        String response = chatClient.prompt()
                .system("You are a strict log parser. Return ONLY a single JSON object. " +
                        "Essential fields: timestamp (yyyy-MM-dd HH:mm:ss.SSS), level, message, source. " +
                        "CRITICAL: If the message contains a stack trace, preserve the key error details in the 'message' field. " +
                        "Use forward slashes or escaped backslashes in paths. No markdown, no intro.")
                .user("Raw line: " + truncateLongMessage(rawLine))
                .call()
                .content();
        
        LogEntry entry = parseJson(response, LogEntry.class);
        
        if (entry != null) {
            entry.setRaw(rawLine);
            entry.setSource(source);
            if (entry.getId() == null || entry.getId().isEmpty()) {
                entry.setId(UUID.randomUUID().toString());
            }
            return entry;
        }
        return LogEntry.builder().raw(rawLine).source(source).message("Failed to parse").build();
    }

    /**
     * Normalize multiple log lines in one AI call (Batch)
     */
    public List<LogEntry> normalizeBatch(List<String> rawLines, String source) {
        log.info("AI Normalizing batch of {} log lines from source: {}", rawLines.size(), source);
        
        String input = rawLines.stream()
                .map(this::truncateLongMessage)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        
        String response = chatClient.prompt()
                .system("You are a strict batch log parser. Return ONLY a JSON array of objects. " +
                        "Essential fields: timestamp (yyyy-MM-dd HH:mm:ss.SSS), level, message, source. " +
                        "CRITICAL: Output ALL entries. NEVER use ellipses '...' or shortened responses. " +
                        "If a log entry is multiline (stack trace), treat it as ONE object with the full trace in 'message'. " +
                        "Always use forward slashes for paths. No markdown, no intro.")
                .user("Internal logs:\n" + input)
                .call()
                .content();
        
        List<LogEntry> entries = parseJson(response, new ParameterizedTypeReference<List<LogEntry>>() {});
        
        if (entries == null) {
            log.warn("Failed to parse AI response for source: {}. Returning empty list.", source);
            return Collections.emptyList();
        }
        
        // Ensure every entry in the list has a unique ID, source, etc.
        for (int i = 0; i < entries.size(); i++) {
            LogEntry entry = entries.get(i);
            if (entry == null) continue;
            
            if (entry.getId() == null || entry.getId().trim().isEmpty()) {
                entry.setId(UUID.randomUUID().toString());
            }
            entry.setSource(source);
            
            // Map original raw line if available (within bounds)
            if (i < rawLines.size()) {
                entry.setRaw(rawLines.get(i));
            }
        }
        
        // Remove dummy/empty entries the AI might have hallucinated
        entries.removeIf(e -> e.getMessage() == null || e.getMessage().trim().isEmpty());
        
        return entries;
    }

    /**
     * Helper to clean AI response and parse JSON
     */
    private <T> T parseJson(String text, Object type) {
        try {
            // Find the first '[' or '{' and the last ']' or '}'
            int startArray = text.indexOf('[');
            int startObject = text.indexOf('{');
            int start = (startArray != -1 && (startObject == -1 || startArray < startObject)) ? startArray : startObject;
            
            int endArray = text.lastIndexOf(']');
            int endObject = text.lastIndexOf('}');
            int end = Math.max(endArray, endObject);
            
            if (start != -1 && end != -1 && end > start) {
                String cleanJson = text.substring(start, end + 1);
                
                // Remove AI-inserted ellipses like "... ," or ", ..."
                cleanJson = cleanJson.replaceAll(",\\s*\\.\\.\\.", "");
                cleanJson = cleanJson.replaceAll("\\.\\.\\.\\s*,", "");
                cleanJson = cleanJson.replaceAll("\\.\\.\\.", "");
                
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                // Crucial: allow backslashes before any character (like Windows paths \C)
                mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
                
                // CRITICAL FOR EPOCH PARSING:
                // Allow AI to return numeric epoch time instead of Strings
                mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
                // When parsing epoch ms to LocalDateTime, Jackson needs a TimeZone context
                mapper.setTimeZone(java.util.TimeZone.getDefault());
                
                // Ignore extra fields AI might invent
                mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                
                if (type instanceof Class) {
                    return mapper.readValue(cleanJson, (Class<T>) type);
                } else if (type instanceof ParameterizedTypeReference) {
                    return mapper.readValue(cleanJson, mapper.getTypeFactory().constructType(((ParameterizedTypeReference<?>) type).getType()));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse cleaned JSON: {}. Error: {}", text, e.getMessage());
        }
        return null;
    }

    /**
     * Prevent hitting AI token limits by truncating extremely long stack traces or messages.
     */
    private String truncateLongMessage(String msg) {
        if (msg == null) return "";
        int MAX_LEN = 2500; 
        if (msg.length() > MAX_LEN) {
            log.warn("Truncating extremely long log entry (length: {}) to stay within AI limits.", msg.length());
            return msg.substring(0, MAX_LEN) + "\n...[TRUNCATED DUE TO LENGTH]";
        }
        return msg;
    }
}
