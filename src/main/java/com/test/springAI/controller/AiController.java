package com.test.springAI.controller;

import com.test.springAI.model.ChatRequest;
import com.test.springAI.model.ChatResponse;
import com.test.springAI.model.EmbeddingRequest;
import com.test.springAI.model.RagRequest;
import com.test.springAI.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * =============================================================
 * AiController - REST API cho tất cả Spring AI features
 * =============================================================
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final ChatService chatService;
    private final PromptService promptService;
    private final EmbeddingService embeddingService;
    private final RagService ragService;
    private final FunctionCallService functionCallService;
    private final MultiModalService multiModalService;

    // =========================================================
    // CHAT ENDPOINTS
    // =========================================================

    /** POST /api/ai/chat - Simple chat */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("POST /api/ai/chat: {}", request.getMessage());
        String answer = chatService.simpleChat(request.getMessage());
        return ResponseEntity.ok(chatService.buildResponse(null, answer));
    }

    /** POST /api/ai/chat/system - Chat với system prompt */
    @PostMapping("/chat/system")
    public ResponseEntity<ChatResponse> chatWithSystem(@RequestBody ChatRequest request) {
        String systemPrompt = request.getSystemPrompt() != null
                ? request.getSystemPrompt()
                : "Bạn là trợ lý AI hữu ích, trả lời bằng tiếng Việt.";
        String answer = chatService.chatWithSystem(systemPrompt, request.getMessage());
        return ResponseEntity.ok(chatService.buildResponse(null, answer));
    }

    /** POST /api/ai/chat/memory - Chat có nhớ lịch sử */
    @PostMapping("/chat/memory")
    public ResponseEntity<ChatResponse> chatWithMemory(@RequestBody ChatRequest request) {
        String convId = request.getConversationId() != null
                ? request.getConversationId()
                : "default-session";
        String answer = chatService.chatWithMemory(convId, request.getMessage());
        return ResponseEntity.ok(chatService.buildResponse(convId, answer));
    }

    /** GET /api/ai/stream?message=... - Streaming SSE */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam String message) {
        log.info("GET /api/ai/stream: {}", message);
        return chatService.streamChat(message);
    }

    // =========================================================
    // PROMPT ENDPOINTS
    // =========================================================

    /** POST /api/ai/prompt/template - Prompt Template */
    @PostMapping("/prompt/template")
    public ResponseEntity<Map<String, String>> promptTemplate(
            @RequestParam String template,
            @RequestBody Map<String, Object> vars) {
        String result = promptService.templatePrompt(template, vars);
        return ResponseEntity.ok(Map.of("result", result));
    }

    /** POST /api/ai/prompt/translate - Dịch văn bản */
    @PostMapping("/prompt/translate")
    public ResponseEntity<Map<String, String>> translate(
            @RequestParam String language,
            @RequestBody Map<String, String> body) {
        String result = promptService.translateTemplate(body.get("text"), language);
        return ResponseEntity.ok(Map.of("result", result));
    }

    /** POST /api/ai/prompt/summarize - Tóm tắt văn bản */
    @PostMapping("/prompt/summarize")
    public ResponseEntity<Map<String, String>> summarize(
            @RequestParam(defaultValue = "3") int maxSentences,
            @RequestBody Map<String, String> body) {
        String result = promptService.summarizeTemplate(body.get("text"), maxSentences);
        return ResponseEntity.ok(Map.of("result", result));
    }

    /** POST /api/ai/prompt/cot - Chain-of-Thought */
    @PostMapping("/prompt/cot")
    public ResponseEntity<Map<String, String>> chainOfThought(@RequestBody Map<String, String> body) {
        String result = promptService.chainOfThoughtPrompt(body.get("question"));
        return ResponseEntity.ok(Map.of("result", result));
    }

    /** POST /api/ai/prompt/sentiment - Sentiment analysis (few-shot) */
    @PostMapping("/prompt/sentiment")
    public ResponseEntity<Map<String, String>> sentiment(@RequestBody Map<String, String> body) {
        String result = promptService.sentimentAnalysisFewShot(body.get("text"));
        return ResponseEntity.ok(Map.of("sentiment", result));
    }

    // =========================================================
    // EMBEDDING ENDPOINTS
    // =========================================================

    /** POST /api/ai/embed - Tạo embedding vector */
    @PostMapping("/embed")
    public ResponseEntity<Map<String, Object>> embed(@RequestBody EmbeddingRequest request) {
        log.info("POST /api/ai/embed");
        if (request.getText() != null) {
            List<Double> vector = embeddingService.embedAsDoubleList(request.getText());
            return ResponseEntity.ok(Map.of(
                    "text", request.getText(),
                    "dimensions", vector.size(),
                    "vector", vector));
        } else {
            List<float[]> vectors = embeddingService.embedBatch(request.getTexts());
            return ResponseEntity.ok(Map.of(
                    "count", vectors.size(),
                    "dimensions", vectors.isEmpty() ? 0 : vectors.get(0).length));
        }
    }

    /** POST /api/ai/embed/similarity - So sánh 2 văn bản */
    @PostMapping("/embed/similarity")
    public ResponseEntity<Map<String, Object>> similarity(@RequestBody Map<String, String> body) {
        double score = embeddingService.textSimilarity(body.get("textA"), body.get("textB"));
        return ResponseEntity.ok(Map.of(
                "textA", body.get("textA"),
                "textB", body.get("textB"),
                "similarity", score));
    }

    // =========================================================
    // RAG ENDPOINTS
    // =========================================================

    /** POST /api/ai/rag/load - Load documents vào VectorStore */
    @PostMapping("/rag/load")
    public ResponseEntity<Map<String, Object>> loadDocuments(@RequestBody RagRequest request) {
        log.info("POST /api/ai/rag/load: {} documents", request.getDocuments().size());
        ragService.loadDocuments(request.getDocuments());
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "loaded", request.getDocuments().size()));
    }

    /** POST /api/ai/rag/query - RAG query */
    @PostMapping("/rag/query")
    public ResponseEntity<ChatResponse> ragQuery(@RequestBody RagRequest request) {
        log.info("POST /api/ai/rag/query: {}", request.getQuestion());
        String answer = ragService.ragQuery(request.getQuestion());
        return ResponseEntity.ok(chatService.buildResponse(null, answer));
    }

    /** POST /api/ai/rag/query/advisor - RAG với QuestionAnswerAdvisor */
    @PostMapping("/rag/query/advisor")
    public ResponseEntity<ChatResponse> ragQueryAdvisor(@RequestBody RagRequest request) {
        String answer = ragService.ragWithAdvisor(request.getQuestion(), 3);
        return ResponseEntity.ok(chatService.buildResponse(null, answer));
    }

    /** POST /api/ai/rag/retrieve - Chỉ retrieve, không generate */
    @PostMapping("/rag/retrieve")
    public ResponseEntity<Map<String, Object>> retrieve(@RequestBody RagRequest request) {
        List<String> docs = ragService.retrieveTexts(request.getQuestion(), 5);
        return ResponseEntity.ok(Map.of("question", request.getQuestion(), "documents", docs));
    }

    // =========================================================
    // FUNCTION CALLING ENDPOINTS
    // =========================================================

    /** POST /api/ai/tools/weather - Weather tool */
    @PostMapping("/tools/weather")
    public ResponseEntity<ChatResponse> chatWeather(@RequestBody ChatRequest request) {
        String answer = functionCallService.chatWithWeatherTool(request.getMessage());
        return ResponseEntity.ok(chatService.buildResponse(null, answer));
    }

    /** POST /api/ai/tools/calc - Calculator tool */
    @PostMapping("/tools/calc")
    public ResponseEntity<ChatResponse> chatCalculator(@RequestBody ChatRequest request) {
        String answer = functionCallService.chatWithCalculatorTool(request.getMessage());
        return ResponseEntity.ok(chatService.buildResponse(null, answer));
    }

    /** POST /api/ai/tools/time - Time tool */
    @PostMapping("/tools/time")
    public ResponseEntity<ChatResponse> chatTime(@RequestBody ChatRequest request) {
        String answer = functionCallService.chatWithTimeTool(request.getMessage());
        return ResponseEntity.ok(chatService.buildResponse(null, answer));
    }

    /** POST /api/ai/tools/all - Tất cả tools */
    @PostMapping("/tools/all")
    public ResponseEntity<ChatResponse> chatAllTools(@RequestBody ChatRequest request) {
        String answer = functionCallService.chatWithAllTools(request.getMessage());
        return ResponseEntity.ok(chatService.buildResponse(null, answer));
    }

    // =========================================================
    // MULTIMODAL ENDPOINTS
    // =========================================================

    /** POST /api/ai/vision/url - Phân tích ảnh từ URL */
    @PostMapping("/vision/url")
    public ResponseEntity<ChatResponse> analyzeImageUrl(
            @RequestParam String imageUrl,
            @RequestBody ChatRequest request) {
        String answer = multiModalService.analyzeImageUrl(imageUrl, request.getMessage());
        return ResponseEntity.ok(chatService.buildResponse(null, answer));
    }

    /** POST /api/ai/vision/describe - Mô tả ảnh */
    @PostMapping("/vision/describe")
    public ResponseEntity<ChatResponse> describeImage(@RequestParam String imageUrl) {
        String answer = multiModalService.describeImage(imageUrl);
        return ResponseEntity.ok(chatService.buildResponse(null, answer));
    }

    // =========================================================
    // [PgVector] OPTION A — CHAT với context từ PgVector
    // =========================================================

    /**
     * [PgVector] POST /api/ai/chat/pgvector
     * Chat tự động lấy context liên quan từ PgVectorStore trước khi trả lời.
     * Khác /rag/query ở chỗ: được tích hợp trực tiếp trong ChatService.
     *
     * Body: { "message": "câu hỏi", "topK": 3, "threshold": 0.0 }
     */
    @PostMapping("/chat/pgvector")
    public ResponseEntity<ChatResponse> chatWithPgVector(@RequestBody ChatRequest request) {
        log.info("[PgVector] POST /api/ai/chat/pgvector: {}", request.getMessage());
        int topK = (request.getTopK() != null) ? request.getTopK() : 3;
        double threshold = (request.getThreshold() != null) ? request.getThreshold() : 0.0;
        String answer = chatService.chatWithPgVectorContext(request.getMessage(), topK, threshold);
        return ResponseEntity.ok(chatService.buildResponse(null, answer));
    }

    // =========================================================
    // [PgVector] OPTION B — EMBED với metadata filter
    // =========================================================

    /**
     * [PgVector] POST /api/ai/embed/search-filter
     * Semantic search với metadata filter trong PgVectorStore.
     *
     * Body: { "query": "...", "topK": 5, "threshold": 0.0, "filter": "source ==
     * 'input.txt'" }
     */
    @PostMapping("/embed/search-filter")
    public ResponseEntity<Map<String, Object>> searchWithFilter(@RequestBody Map<String, Object> body) {
        log.info("[PgVector] POST /api/ai/embed/search-filter");
        String query = (String) body.get("query");
        int topK = body.containsKey("topK") ? (int) body.get("topK") : 5;
        double threshold = body.containsKey("threshold") ? ((Number) body.get("threshold")).doubleValue() : 0.0;
        String filter = (String) body.getOrDefault("filter", null);

        List<org.springframework.ai.document.Document> docs = (filter != null && !filter.isBlank())
                ? embeddingService.semanticSearchWithFilter(query, topK, threshold, filter)
                : embeddingService.semanticSearch(query, topK, threshold);

        List<String> texts = docs.stream()
                .map(d -> d.getText() != null ? d.getText() : "")
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(Map.of("query", query, "filter", filter != null ? filter : "", "results", texts));
    }

    /**
     * [PgVector] DELETE /api/ai/embed/delete
     * Xóa documents khỏi PgVectorStore theo danh sách ID.
     *
     * Body: { "ids": ["uuid1", "uuid2"] }
     */
    @DeleteMapping("/embed/delete")
    public ResponseEntity<Map<String, Object>> deleteEmbeddings(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        log.info("[PgVector] DELETE /api/ai/embed/delete: {} ids", ids.size());
        embeddingService.deleteDocuments(ids);
        return ResponseEntity.ok(Map.of("status", "deleted", "count", ids.size()));
    }

    // =========================================================
    // [PgVector] OPTION C — RAG Document Management
    // =========================================================

    /**
     * [PgVector] DELETE /api/ai/rag/delete
     * Xóa documents khỏi PgVectorStore theo ID.
     *
     * Body: { "ids": ["uuid1", "uuid2"] } hoặc { "all": true } để xóa tất cả
     */
    @DeleteMapping("/rag/delete")
    public ResponseEntity<Map<String, Object>> deleteDocuments(@RequestBody Map<String, Object> body) {
        log.info("[PgVector] DELETE /api/ai/rag/delete");
        Boolean all = (Boolean) body.getOrDefault("all", false);
        if (Boolean.TRUE.equals(all)) {
            ragService.deleteAllDocuments();
            return ResponseEntity.ok(Map.of("status", "all_deleted"));
        }
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        ragService.deleteDocuments(ids);
        return ResponseEntity.ok(Map.of("status", "deleted", "count", ids.size()));
    }

    /**
     * [PgVector] GET /api/ai/rag/count
     * Đếm số documents đang có trong PgVectorStore.
     */
    @GetMapping("/rag/count")
    public ResponseEntity<Map<String, Object>> countDocuments() {
        log.info("[PgVector] GET /api/ai/rag/count");
        long count = ragService.countDocuments();
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * [PgVector] POST /api/ai/rag/retrieve/filter
     * Retrieve documents với metadata filter rồi trả về text.
     *
     * Body: { "question": "...", "topK": 5, "filter": "source == 'input.txt'" }
     */
    @PostMapping("/rag/retrieve/filter")
    public ResponseEntity<Map<String, Object>> retrieveWithFilter(@RequestBody RagRequest request) {
        log.info("[PgVector] POST /api/ai/rag/retrieve/filter: filter={}", request.getFilter());
        List<org.springframework.ai.document.Document> docs = ragService.retrieveDocumentsWithFilter(
                request.getQuestion(),
                request.getTopK() != null ? request.getTopK() : 5,
                request.getFilter());
        List<String> texts = docs.stream()
                .map(d -> d.getText() != null ? d.getText() : "")
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(Map.of("question", request.getQuestion(),
                "filter", request.getFilter() != null ? request.getFilter() : "",
                "documents", texts));
    }
}
