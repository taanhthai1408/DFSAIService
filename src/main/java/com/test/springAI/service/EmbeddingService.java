package com.test.springAI.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * =============================================================
 * EmbeddingService - Vector embeddings & semantic search
 * =============================================================
 *
 * Embedding chuyển đổi text thành vector số thực (float[]).
 * Vectors gần nhau trong không gian = semantically similar texts.
 *
 * Ứng dụng:
 * - Semantic search
 * - Document similarity
 * - Clustering & classification
 * - RAG (Retrieval Augmented Generation)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final SimpleVectorStore simpleVectorStore;

    // =========================================================
    // 1. EMBED SINGLE TEXT
    // =========================================================

    /**
     * Chuyển đổi một text thành vector embedding.
     *
     * @param text Text cần embed
     * @return float[] - vector biểu diễn text (dim phụ thuộc model)
     */
    public float[] embed(String text) {
        log.debug("Embedding single text (len={})", text.length());
        return embeddingModel.embed(text);
    }

    /**
     * Embed text và trả về dưới dạng List<Double> (dễ serialize thành JSON).
     */
    public List<Double> embedAsDoubleList(String text) {
        float[] vector = embed(text);
        List<Double> result = new ArrayList<>(vector.length);
        for (float v : vector) {
            result.add((double) v);
        }
        return result;
    }

    // =========================================================
    // 2. EMBED MULTIPLE TEXTS (BATCH)
    // =========================================================

    /**
     * Embed nhiều texts cùng lúc.
     * Hiệu quả hơn gọi embed() nhiều lần riêng lẻ.
     *
     * @param texts Danh sách texts cần embed
     * @return List<float[]> - danh sách vector tương ứng
     */
    public List<float[]> embedBatch(List<String> texts) {
        log.debug("Embedding batch of {} texts", texts.size());
        EmbeddingRequest request = new EmbeddingRequest(texts, OllamaOptions.builder().build());
        EmbeddingResponse response = embeddingModel.call(request);
        return response.getResults().stream()
                .map(e -> e.getOutput())
                .collect(Collectors.toList());
    }

    // =========================================================
    // 3. COSINE SIMILARITY
    // =========================================================

    /**
     * Tính Cosine Similarity giữa 2 vectors.
     * Kết quả: -1.0 (ngược chiều) → 0.0 (vuông góc) → 1.0 (cùng chiều/giống nhau)
     * Texts giống nhau → similarity gần 1.0
     *
     * @param vecA Vector thứ 1
     * @param vecB Vector thứ 2
     * @return double trong [0.0, 1.0] (hoặc [-1.0, 1.0] chính xác)
     */
    public double cosineSimilarity(float[] vecA, float[] vecB) {
        if (vecA.length != vecB.length) {
            throw new IllegalArgumentException(
                    "Vectors must have same dimension: " + vecA.length + " vs " + vecB.length);
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vecA.length; i++) {
            dotProduct += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }

        if (normA == 0.0 || normB == 0.0)
            return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Tính similarity trực tiếp từ 2 texts (embed rồi tính similarity).
     */
    public double textSimilarity(String textA, String textB) {
        float[] vecA = embed(textA);
        float[] vecB = embed(textB);
        double sim = cosineSimilarity(vecA, vecB);
        log.debug("Similarity '{}...' vs '{}...' = {}",
                textA.substring(0, Math.min(20, textA.length())),
                textB.substring(0, Math.min(20, textB.length())), sim);
        return sim;
    }

    // =========================================================
    // 4. FIND MOST SIMILAR
    // =========================================================

    /**
     * Tìm text giống nhất với query trong danh sách candidates.
     *
     * @param query      Text truy vấn
     * @param candidates Danh sách candidates cần so sánh
     * @return Text trong candidates có similarity cao nhất với query
     */
    public String findMostSimilar(String query, List<String> candidates) {
        log.debug("FindMostSimilar: {} candidates", candidates.size());
        float[] queryVec = embed(query);

        String bestMatch = null;
        double bestScore = Double.MIN_VALUE;

        for (String candidate : candidates) {
            float[] candidateVec = embed(candidate);
            double score = cosineSimilarity(queryVec, candidateVec);
            log.debug("  '{}...' → score={}",
                    candidate.substring(0, Math.min(30, candidate.length())), score);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = candidate;
            }
        }

        log.debug("Best match (score={}): {}...", bestScore,
                bestMatch != null ? bestMatch.substring(0, Math.min(30, bestMatch.length())) : "null");
        return bestMatch;
    }

    /**
     * Tìm Top-K texts giống nhất với query, trả về kèm score.
     */
    public List<SimilarityResult> findTopK(String query, List<String> candidates, int topK) {
        float[] queryVec = embed(query);

        List<SimilarityResult> results = new ArrayList<>();
        for (String candidate : candidates) {
            float[] vec = embed(candidate);
            double score = cosineSimilarity(queryVec, vec);
            results.add(new SimilarityResult(candidate, score));
        }

        return results.stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    // =========================================================
    // 5. VECTOR STORE OPERATIONS
    // =========================================================

    /**
     * Thêm documents vào SimpleVectorStore (in-memory).
     * Documents sẽ được tự động embed và lưu trữ.
     *
     * @param texts Danh sách text cần thêm vào store
     */
    public void addToVectorStore(List<String> texts) {
        log.debug("Adding {} documents to vector store", texts.size());
        List<Document> docs = texts.stream()
                .map(Document::new)
                .collect(Collectors.toList());
        simpleVectorStore.add(docs);
    }

    /**
     * Tìm kiếm semantic trong VectorStore.
     *
     * @param query     Câu hỏi / từ khóa tìm kiếm
     * @param topK      Số kết quả tối đa
     * @param threshold Ngưỡng similarity tối thiểu (0.0 - 1.0)
     * @return Danh sách documents phù hợp nhất
     */
    public List<Document> semanticSearch(String query, int topK, double threshold) {
        log.debug("SemanticSearch: query='{}...', topK={}, threshold={}",
                query.substring(0, Math.min(30, query.length())), topK, threshold);
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();
        return simpleVectorStore.similaritySearch(searchRequest);
    }

    // =========================================================
    // Inner record
    // =========================================================

    /**
     * Kết quả similarity search kèm score.
     */
    public record SimilarityResult(String text, double score) {
    }
}
