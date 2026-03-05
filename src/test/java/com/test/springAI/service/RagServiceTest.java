package com.test.springAI.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RagServiceTest - Unit tests for RagService (Spring AI 1.0.0)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RagService Tests")
class RagServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private SimpleVectorStore simpleVectorStore;

    @Mock
    private ChatMemory chatMemory;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @InjectMocks
    private RagService ragService;

    @BeforeEach
    void setUp() {
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);

        // Mock advisors methods for varargs and consumer
        lenient().when(requestSpec.advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class)))
                .thenReturn(requestSpec);
        lenient().when(requestSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(requestSpec);

        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);
        lenient().when(callResponseSpec.content()).thenReturn("RAG answer");
    }

    // =========================================================
    // 1. loadDocuments
    // =========================================================

    @Test
    @DisplayName("loadDocuments - adds documents to vector store")
    void loadDocuments_shouldAddToVectorStore() {
        List<String> texts = List.of("Document 1", "Document 2", "Document 3");
        ragService.loadDocuments(texts);
        verify(simpleVectorStore).add(argThat(docs -> docs.size() == 3));
    }

    @Test
    @DisplayName("loadDocuments - empty list adds nothing")
    void loadDocuments_emptyList_shouldAddEmptyList() {
        ragService.loadDocuments(Collections.emptyList());
        verify(simpleVectorStore).add(argThat(List::isEmpty));
    }

    @Test
    @DisplayName("loadDocumentsWithMetadata - adds documents with metadata")
    void loadDocumentsWithMetadata_shouldAddDocsWithMeta() {
        List<String> texts = List.of("Doc 1", "Doc 2");
        List<java.util.Map<String, Object>> metas = List.of(
                java.util.Map.of("source", "file1.txt"),
                java.util.Map.of("source", "file2.txt"));
        ragService.loadDocumentsWithMetadata(texts, metas);
        verify(simpleVectorStore).add(argThat(docs -> docs.size() == 2));
    }

    // =========================================================
    // 2. ragQuery
    // =========================================================

    @Test
    @DisplayName("ragQuery - with relevant docs builds augmented prompt")
    void ragQuery_withRelevantDocs_shouldBuildAugmentedPrompt() {
        Document mockDoc = new Document("Spring AI is a framework for AI integration.");
        when(simpleVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(mockDoc));

        String result = ragService.ragQuery("What is Spring AI?", 3);

        assertThat(result).isEqualTo("RAG answer");
        verify(simpleVectorStore).similaritySearch(any(SearchRequest.class));
        verify(chatClient).prompt();
    }

    @Test
    @DisplayName("ragQuery - no docs found falls back to direct question")
    void ragQuery_noDocsFound_shouldFallBackToDirect() {
        when(simpleVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

        String result = ragService.ragQuery("Unknown question");

        assertThat(result).isEqualTo("RAG answer");
        verify(chatClient).prompt();
    }

    @Test
    @DisplayName("ragQuery with default topK=3")
    void ragQuery_defaultTopK_shouldUseTopK3() {
        when(simpleVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());
        ragService.ragQuery("test");
        verify(simpleVectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("ragQuery retrieves documents and incorporates into prompt")
    void ragQuery_withMultipleDocs_shouldConcatenateContext() {
        List<Document> docs = List.of(
                new Document("Document content A"),
                new Document("Document content B"));
        when(simpleVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(docs);

        ragService.ragQuery("question", 2);

        // Verify augmented prompt contains context
        verify(requestSpec).user(argThat(
                (String prompt) -> prompt.contains("Document content A") && prompt.contains("Document content B")));
    }

    // =========================================================
    // 3. ragWithAdvisor
    // =========================================================

    @Test
    @DisplayName("ragWithAdvisor - uses advisors on ChatClient")
    void ragWithAdvisor_shouldUseAdvisors() {
        String result = ragService.ragWithAdvisor("What is RAG?", 3);
        assertThat(result).isEqualTo("RAG answer");
        verify(requestSpec).advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class));
    }

    // =========================================================
    // 4. retrieveDocuments
    // =========================================================

    @Test
    @DisplayName("retrieveDocuments - calls similaritySearch")
    void retrieveDocuments_shouldCallSimilaritySearch() {
        Document doc = new Document("Result document");
        when(simpleVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<Document> result = ragService.retrieveDocuments("query", 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getText()).isEqualTo("Result document");
    }

    @Test
    @DisplayName("retrieveTexts - returns text content of documents")
    void retrieveTexts_shouldReturnTexts() {
        when(simpleVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("Text 1"), new Document("Text 2")));

        List<String> texts = ragService.retrieveTexts("query", 5);

        assertThat(texts).containsExactly("Text 1", "Text 2");
    }

    @Test
    @DisplayName("retrieveDocuments - empty result when store is empty")
    void retrieveDocuments_emptyStore_shouldReturnEmpty() {
        when(simpleVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());
        List<Document> result = ragService.retrieveDocuments("query", 3);
        assertThat(result).isEmpty();
    }
}
