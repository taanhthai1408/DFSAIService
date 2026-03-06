package com.test.springAI.service;

import com.test.springAI.model.LogCriteria;
import com.test.springAI.model.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Adapter for reading logs from an existing SQL database.
 * Supports sources like "db://table_name" or JDBC URLs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseLogAdapter implements LogSourceProvider {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public String getName() {
        return "SQL Database Log Adapter";
    }

    @Override
    public boolean supports(String source) {
        return source != null && (source.startsWith("db://") || source.startsWith("jdbc:"));
    }

    @Override
    public List<LogEntry> fetchLogs(LogCriteria criteria) {
        String source = criteria.getSource();
        String tableName = source.startsWith("db://") ? source.substring(5) : "logs"; // Default to 'logs' table
        
        log.info("Fetching logs from database table: {}", tableName);
        
        List<LogEntry> logs = new ArrayList<>();
        
        // Dynamic query building (caution: SQL injection risk in production)
        // This is a generic implementation for the demo
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName);
        sql.append(" WHERE 1=1");
        
        List<Object> params = new ArrayList<>();
        
        if (criteria.getLevel() != null) {
            sql.append(" AND level = ?");
            params.add(criteria.getLevel());
        }
        
        if (criteria.getStartTime() != null) {
            sql.append(" AND timestamp >= ?");
            params.add(Timestamp.valueOf(criteria.getStartTime()));
        }
        
        if (criteria.getEndTime() != null) {
            sql.append(" AND timestamp <= ?");
            params.add(Timestamp.valueOf(criteria.getEndTime()));
        }
        
        sql.append(" ORDER BY timestamp DESC LIMIT ?");
        params.add(criteria.getLimit());

        try {
            return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
                LocalDateTime ts = rs.getTimestamp("timestamp") != null ? 
                                rs.getTimestamp("timestamp").toLocalDateTime() : LocalDateTime.now();
                
                return LogEntry.builder()
                        .id(rs.getString("id") != null ? rs.getString("id") : UUID.randomUUID().toString())
                        .timestamp(ts)
                        .level(rs.getString("level"))
                        .source(source)
                        .message(rs.getString("message"))
                        .raw(rs.getString("raw_content"))
                        .build();
            });
        } catch (Exception e) {
            log.error("Error querying logs from database: {}", source, e);
            return List.of();
        }
    }

    @Override
    public Flux<LogEntry> streamLogs(String source) {
        // Real-time DB streaming would require Change Data Capture (CDC) 
        // or periodic polling. For now, we return empty.
        return Flux.empty();
    }
}
