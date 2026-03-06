package com.test.springAI.service;

import com.test.springAI.model.LogCriteria;
import com.test.springAI.model.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service that uses AI to process and analyze logs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiLogProcessor {

    private final ChatClient chatClient;
    private final LogIntegrationService logIntegrationService;

    /**
     * Fetch logs from a source and summarize them using AI
     */
    public String summarizeLogs(String source, int limit) {
        log.info("AI Summarizing logs for source: {}, limit: {}", source, limit);
        
        List<LogEntry> logs = logIntegrationService.getLogs(
                LogCriteria.builder().source(source).limit(limit).build()
        );

        if (logs.isEmpty()) {
            return "No logs found for " + source;
        }

        String logContext = logs.stream()
                .map(LogEntry::getRaw)
                .collect(Collectors.joining("\n"));

        return chatClient.prompt()
                .system("You are an expert SRE. Summarize the following log entries. " +
                        "Highlight any errors or anomalies.")
                .user("Analyze these logs from '" + source + "':\n\n" + logContext)
                .call()
                .content();
    }

    /**
     * Perform Root Cause Analysis (RCA) on errors in logs
     */
    public String performRCA(String source) {
        log.info("AI Performing RCA for source: {}", source);
        
        List<LogEntry> errorLogs = logIntegrationService.getLogs(
                LogCriteria.builder().source(source).level("ERROR").limit(10).build()
        );

        if (errorLogs.isEmpty()) {
            return "No recent errors found for " + source + " to analyze.";
        }

        String errorContext = errorLogs.stream()
                .map(l -> String.format("[%s] %s", l.getTimestamp(), l.getMessage()))
                .collect(Collectors.joining("\n"));

        return chatClient.prompt()
                .system("You are a senior developer. Analyze these error logs and suggest probable root causes and fixes.")
                .user("Analyze these errors from '" + source + "':\n\n" + errorContext)
                .call()
                .content();
    }
}
