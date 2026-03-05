package com.test.springAI.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * =============================================================
 * EmbeddingServiceTest - Unit Tests cho EmbeddingService
 * =============================================================
 *
 * Test coverage:
 * 1. embed - single text → float[]
 * 2. embedBatch - multiple texts
 * 3. cosineSimilarity - computation
 * 4. textSimilarity - end-to-end
 * 5. findMostSimilar - semantic search
 * 6. findTopK - top results
 * 7. addToVectorStore
 * 8. semanticSearch
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingService Tests")
class EmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private SimpleVectorStore simpleVectorStore;

    @InjectMocks
    private EmbeddingService embeddingService;

    // Vectors mẫu cho tests
    private static final float[] VEC_A = { 1.0f, 0.0f, 0.0f };
    private static final float[] VEC_B = { 0.0f, 1.0f, 0.0f };
    private static final float[] VEC_SAME = { 1.0f, 0.0f, 0.0f }; // giống VEC_A
    private static final float[] VEC_SPRING_AI = { 0.8f, 0.6f, 0.0f };

    // =========================================================
    // 1. embed
    // =========================================================

    @Test
    @DisplayName("embed - trả về float[] không rỗng")
    void embed_shouldReturnNonEmptyVector() {
        when(embeddingModel.embed(anyString())).thenReturn(VEC_A);

        float[] result = embeddingService.embed("Spring AI");

        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(3);
        verify(embeddingModel).embed("Spring AI");
    }

    @Test
    @DisplayName("embed - gọi model đúng 1 lần")
    void embed_shouldCallModelOnce() {
        when(embeddingModel.embed(anyString())).thenReturn(VEC_A);
        embeddingService.embed("test text");
        verify(embeddingModel, times(1)).embed(anyString());
    }

    @Test
    @DisplayName("embedAsDoubleList - chuyển đổi float[] → List<Double>")
    void embedAsDoubleList_shouldConvertToDoubleList() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[] { 0.5f, 0.3f, 0.2f });

        List<Double> list = embeddingService.embedAsDoubleList("test");

        assertThat(list).hasSize(3);
        assertThat(list.get(0)).isCloseTo(0.5, within(0.001));
    }

    // =========================================================
    // 2. embedBatch
    // =========================================================

    @Test
    @DisplayName("embedBatch - embed nhiều texts cùng lúc")
    void embedBatch_shouldEmbedMultipleTexts() {
        EmbeddingResponse mockResponse = mock(EmbeddingResponse.class);
        Embedding emb1 = mock(Embedding.class);
        Embedding emb2 = mock(Embedding.class);

        when(embeddingModel.call(any())).thenReturn(mockResponse);
        when(mockResponse.getResults()).thenReturn(List.of(emb1, emb2));
        when(emb1.getOutput()).thenReturn(VEC_A);
        when(emb2.getOutput()).thenReturn(VEC_B);

        List<float[]> results = embeddingService.embedBatch(List.of("text1", "text2"));

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isEqualTo(VEC_A);
        assertThat(results.get(1)).isEqualTo(VEC_B);
    }

    // =========================================================
    // 3. cosineSimilarity
    // =========================================================

    @Test
    @DisplayName("cosineSimilarity - 2 vectors giống nhau → 1.0")
    void cosineSimilarity_identicalVectors_shouldReturnOne() {
        double similarity = embeddingService.cosineSimilarity(VEC_A, VEC_SAME);
        assertThat(similarity).isCloseTo(1.0, within(0.0001));
    }

    @Test
    @DisplayName("cosineSimilarity - 2 vectors vuông góc → 0.0")
    void cosineSimilarity_perpendicularVectors_shouldReturnZero() {
        double similarity = embeddingService.cosineSimilarity(VEC_A, VEC_B);
        assertThat(similarity).isCloseTo(0.0, within(0.0001));
    }

    @Test
    @DisplayName("cosineSimilarity - kết quả trong [-1.0, 1.0]")
    void cosineSimilarity_shouldBeInValidRange() {
        float[] vecC = { 0.3f, 0.5f, 0.8f };
        float[] vecD = { 0.1f, 0.9f, 0.4f };

        double similarity = embeddingService.cosineSimilarity(vecC, vecD);

        assertThat(similarity).isBetween(-1.0, 1.0);
    }

    @Test
    @DisplayName("cosineSimilarity - vectors khác dimension → exception")
    void cosineSimilarity_differentDimensions_shouldThrow() {
        float[] vec3d = { 1.0f, 0.0f, 0.0f };
        float[] vec2d = { 1.0f, 0.0f };

        assertThatThrownBy(() -> embeddingService.cosineSimilarity(vec3d, vec2d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimension");
    }

    // =========================================================
    // 4. textSimilarity
    // =========================================================

    @Test
    @DisplayName("textSimilarity - 2 texts giống nghĩa → similarity cao")
    void textSimilarity_similarTexts_shouldHaveHighSimilarity() {
        when(embeddingModel.embed("Spring AI framework"))
                .thenReturn(new float[] { 0.8f, 0.6f, 0.0f });
        when(embeddingModel.embed("Spring AI library"))
                .thenReturn(new float[] { 0.75f, 0.65f, 0.0f });

        double similarity = embeddingService.textSimilarity("Spring AI framework", "Spring AI library");

        assertThat(similarity).isGreaterThan(0.9);
    }

    @Test
    @DisplayName("textSimilarity - 2 texts khác nghĩa → similarity thấp")
    void textSimilarity_differentTexts_shouldHaveLowerSimilarity() {
        when(embeddingModel.embed("Java programming")).thenReturn(new float[] { 1.0f, 0.0f, 0.0f });
        when(embeddingModel.embed("Cooking recipes")).thenReturn(new float[] { 0.0f, 1.0f, 0.0f });

        double similarity = embeddingService.textSimilarity("Java programming", "Cooking recipes");

        assertThat(similarity).isLessThan(0.1);
    }

    // =========================================================
    // 5. findMostSimilar
    // =========================================================

    @Test
    @DisplayName("findMostSimilar - trả về candidate giống nhất")
    void findMostSimilar_shouldReturnMostSimilarCandidate() {
        String query = "lập trình Java";
        List<String> candidates = List.of(
                "Python programming language",
                "Java development guide",
                "Cooking Italian food");

        // Mock: Java development guide gần với query nhất
        when(embeddingModel.embed("lập trình Java")).thenReturn(new float[] { 1.0f, 0.0f, 0.0f });
        when(embeddingModel.embed("Python programming language")).thenReturn(new float[] { 0.7f, 0.3f, 0.0f });
        when(embeddingModel.embed("Java development guide")).thenReturn(new float[] { 0.95f, 0.05f, 0.0f });
        when(embeddingModel.embed("Cooking Italian food")).thenReturn(new float[] { 0.0f, 0.1f, 0.99f });

        String result = embeddingService.findMostSimilar(query, candidates);

        assertThat(result).isEqualTo("Java development guide");
    }

    @Test
    @DisplayName("findMostSimilar - chỉ 1 candidate → trả về candidate đó")
    void findMostSimilar_singleCandidate_shouldReturnIt() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[] { 1.0f });

        String result = embeddingService.findMostSimilar("query", List.of("only candidate"));

        assertThat(result).isEqualTo("only candidate");
    }

    // =========================================================
    // 6. findTopK
    // =========================================================

    @Test
    @DisplayName("findTopK - trả về đúng K results, sắp xếp theo score")
    void findTopK_shouldReturnSortedTopKResults() {
        List<String> candidates = List.of("A", "B", "C", "D");

        when(embeddingModel.embed("query")).thenReturn(new float[] { 1.0f, 0.0f });
        when(embeddingModel.embed("A")).thenReturn(new float[] { 0.9f, 0.1f }); // score ~0.99
        when(embeddingModel.embed("B")).thenReturn(new float[] { 0.5f, 0.5f }); // score ~0.71
        when(embeddingModel.embed("C")).thenReturn(new float[] { 0.1f, 0.9f }); // score ~0.1
        when(embeddingModel.embed("D")).thenReturn(new float[] { 0.8f, 0.2f }); // score ~0.97

        List<EmbeddingService.SimilarityResult> topK = embeddingService.findTopK("query", candidates, 2);

        assertThat(topK).hasSize(2);
        assertThat(topK.get(0).score()).isGreaterThan(topK.get(1).score()); // sorted desc
    }

    // =========================================================
    // 7. addToVectorStore
    // =========================================================

    @Test
    @DisplayName("addToVectorStore - gọi vectorStore.add với đúng số documents")
    void addToVectorStore_shouldAddDocuments() {
        List<String> texts = List.of("Document 1", "Document 2", "Document 3");
        doNothing().when(simpleVectorStore).add(anyList());

        embeddingService.addToVectorStore(texts);

        verify(simpleVectorStore).add(argThat(docs -> docs.size() == 3));
    }

    // =========================================================
    // 8. semanticSearch
    // =========================================================

    @Test
    @DisplayName("semanticSearch - gọi similaritySearch với đúng params")
    void semanticSearch_shouldCallSimilaritySearch() {
        Document doc = new Document("Spring AI hỗ trợ Ollama");
        when(simpleVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

        List<Document> results = embeddingService.semanticSearch("Spring AI", 3, 0.5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText()).contains("Spring AI");
        verify(simpleVectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("semanticSearch - không tìm thấy → trả về list rỗng")
    void semanticSearch_noResults_shouldReturnEmpty() {
        when(simpleVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        List<Document> results = embeddingService.semanticSearch("unknown query", 5, 0.9);

        assertThat(results).isEmpty();
    }
}
