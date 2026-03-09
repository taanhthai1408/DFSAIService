package com.test.springAI.controller;

import com.test.springAI.model.LogEntry;
import com.test.springAI.service.AiNormalizationService;
import com.test.springAI.service.LogVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Endpoint for receiving logs pushed by Filebeat/Logstash.
 */
@Slf4j
@RestController
@RequestMapping("/api/logs/filebeat")
@RequiredArgsConstructor
public class FilebeatController {

    private final com.test.springAI.service.AsyncLogProcessingService asyncLogProcessingService;

    @PostMapping(value = "/_bulk", produces = "application/json")
    public ResponseEntity<String> receiveElasticsearchBulkLogs(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Content-Encoding", required = false) String encoding,
            @RequestBody byte[] payloadBytes) {
        
        String ndjsonPayload = "";
        try {
            if ("gzip".equalsIgnoreCase(encoding)) {
                try (java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(payloadBytes));
                     java.io.InputStreamReader isr = new java.io.InputStreamReader(gis, java.nio.charset.StandardCharsets.UTF_8);
                     java.io.BufferedReader br = new java.io.BufferedReader(isr)) {
                    
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    ndjsonPayload = sb.toString();
                }
            } else {
                ndjsonPayload = new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to decode payload: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Failed to decode payload");
        }

        if (ndjsonPayload == null || ndjsonPayload.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Empty bulk payload from Filebeat");
        }

        // Filebeat Elasticsearch output sends NDJSON (Newline Delimited JSON).
        // Each pair of lines is an action/metadata line followed by the document/source line.
        String[] lines = ndjsonPayload.split("\\r?\\n");
        List<String> rawLogLines = new ArrayList<>();
        String source = "Filebeat";
        
        log.info("Received Elasticsearch bulk payload with {} lines", lines.length);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            try {
                com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(line);
                
                // Skip the Elastic bulk action metadata lines (which have {"index":{...}} or {"create":{...}})
                if (json.has("index") || json.has("create")) {
                    continue;
                }
                
                // Extract actual log data
                if (json.has("message") || json.has("log")) {
                    if (json.has("message")) {
                        String message = json.get("message").asText();
                        if (message != null && !message.trim().isEmpty()) {
                            rawLogLines.add(message);
                        }
                    }
                    
                    if (json.has("log")) {
                        com.fasterxml.jackson.databind.JsonNode logMeta = json.get("log");
                        if (logMeta.has("file")) {
                            com.fasterxml.jackson.databind.JsonNode fileMeta = logMeta.get("file");
                            if (fileMeta.has("path")) {
                                source = fileMeta.get("path").asText();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore parse errors on individual lines to allow the rest to succeed
                log.debug("Line is not a valid log document: {}", e.getMessage());
            }
        }

        if (rawLogLines.isEmpty()) {
            // Must return a valid Elasticsearch-like successful response so Filebeat doesn't retry
            return ResponseEntity.ok("{\"errors\": false, \"items\": []}");
        }

        log.info("Extracted {} valid log messages. Queuing for Async AI processing...", rawLogLines.size());

        // Fire and forget - hand off to background thread pool
        asyncLogProcessingService.processLogsAsynchronously(rawLogLines, source);

        // Return a mock Elasticsearch bulk response
        String successResponse = "{\"errors\": false, \"items\": [{\"index\": {\"status\": 200}}]}";
        return ResponseEntity.ok(successResponse);
    }
    
    // Filebeat checks the root endpoint (with or without trailing slash) to verify Elasticsearch version before sending data
    @RequestMapping(value = {"", "/"}, method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.HEAD}, produces = "application/json")
    public ResponseEntity<String> mockElasticsearchInfo() {
        return ResponseEntity.ok("{\n" +
                "  \"name\" : \"spring-ai-log-node\",\n" +
                "  \"cluster_name\" : \"spring-ai-log-cluster\",\n" +
                "  \"cluster_uuid\" : \"mock-uuid-123\",\n" +
                "  \"version\" : {\n" +
                "    \"number\" : \"8.10.0\",\n" +
                "    \"build_flavor\" : \"default\",\n" +
                "    \"build_type\" : \"docker\",\n" +
                "    \"build_hash\" : \"abcdef\",\n" +
                "    \"build_date\" : \"2023-01-01T00:00:00.000Z\",\n" +
                "    \"build_snapshot\" : false,\n" +
                "    \"lucene_version\" : \"9.7.0\",\n" +
                "    \"minimum_wire_compatibility_version\" : \"7.17.0\",\n" +
                "    \"minimum_index_compatibility_version\" : \"7.0.0\"\n" +
                "  },\n" +
                "  \"tagline\" : \"You Know, for Search\"\n" +
                "}");
    }

    // Catch-all for any other Elasticsearch API requests Filebeat might make (e.g. ILM, templates, monitoring)
    @RequestMapping(value = "/**", method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST, org.springframework.web.bind.annotation.RequestMethod.PUT, org.springframework.web.bind.annotation.RequestMethod.HEAD}, produces = "application/json")
    public ResponseEntity<String> catchAllElasticsearchRequests() {
        return ResponseEntity.ok("{}");
    }
}
