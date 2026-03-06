package com.test.springAI.service;

import com.test.springAI.model.LogCriteria;
import com.test.springAI.model.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Adapter for reading logs from local files.
 */
@Slf4j
@Service
public class FileLogAdapter implements LogSourceProvider {

    @Override
    public String getName() {
        return "Local File Log Adapter";
    }

    @Override
    public boolean supports(String source) {
        if (source == null) return false;
        String pathStr = source.startsWith("file://") ? source.substring(7) : source;
        try {
            Path path = Paths.get(pathStr);
            return path.isAbsolute() && Files.exists(path) && Files.isReadable(path);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<LogEntry> fetchLogs(LogCriteria criteria) {
        String source = criteria.getSource();
        String pathStr = source.startsWith("file://") ? source.substring(7) : source;
        List<LogEntry> logs = new ArrayList<>();
        
        try (Stream<String> lines = Files.lines(Paths.get(pathStr))) {
            lines.limit(criteria.getLimit())
                 .forEach(line -> logs.add(LogEntry.builder()
                         .raw(line)
                         .source(source)
                         .build()));
        } catch (IOException e) {
            log.error("Error reading log file: {}", pathStr, e);
        }
        
        return logs;
    }

    @Override
    public Flux<LogEntry> streamLogs(String source) {
        // Tailing a file would require a more complex implementation 
        // with WatchService or Apache Commons IO Tailer.
        // For simplicity, we return empty or just a one-shot current content.
        return Flux.empty();
    }
    
    /**
     * Helper to read raw lines from a file
     */
    public List<String> readRawLines(String source, int limit) throws IOException {
        try (Stream<String> lines = getLinesStream(source)) {
            return lines.limit(limit).toList();
        }
    }

    /**
     * Helper to read all lines from a file
     */
    public List<String> readAllLines(String source) throws IOException {
        try (Stream<String> lines = getLinesStream(source)) {
            return lines.toList();
        }
    }

    /**
     * Opens a stream of lines from the file.
     * CRITICAL: The caller MUST close this stream (use try-with-resources).
     */
    public Stream<String> getLinesStream(String source) throws IOException {
        String pathStr = source.startsWith("file://") ? source.substring(7) : source;
        return Files.lines(Paths.get(pathStr));
    }
}
