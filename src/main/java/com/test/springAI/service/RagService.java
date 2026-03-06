package com.test.springAI.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RagService - Retrieval Augmented Generation (RAG) with Spring AI 1.0.0
 *
 * Spring AI 1.0.0 uses RetrievalAugmentationAdvisor +
 * VectorStoreDocumentRetriever
 * instead of the old QuestionAnswerAdvisor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

        private final ChatClient chatClient;
        private final VectorStore vectorStore;
        private final ChatMemory chatMemory;

        // =========================================================
        // 1. LOAD DOCUMENTS INTO VECTOR STORE
        // =========================================================

        public void loadDocuments(List<String> texts) {
                log.debug("Loading {} documents into vector store", texts.size());
                List<Document> docs = texts.stream()
                                .map(text -> new Document(text))
                                .collect(Collectors.toList());
                vectorStore.add(docs);
                log.info("Loaded {} documents into vector store", docs.size());
        }

        public void loadDocumentsWithMetadata(List<String> texts, List<java.util.Map<String, Object>> metadatas) {
                log.debug("Loading {} documents with metadata", texts.size());
                List<Document> docs = new java.util.ArrayList<>();
                for (int i = 0; i < texts.size(); i++) {
                        java.util.Map<String, Object> meta = (i < metadatas.size()) ? metadatas.get(i)
                                        : java.util.Map.of();
                        docs.add(new Document(texts.get(i), meta));
                }
                vectorStore.add(docs);
        }

        // =========================================================
        // 2. RAG QUERY - MANUAL PIPELINE
        // =========================================================

        public String ragQuery(String question, int topK) {
                log.debug("RAG query: '{}', topK={}", question.substring(0, Math.min(50, question.length())), topK);

                SearchRequest searchRequest = SearchRequest.builder()
                                .query(question)
                                .topK(topK)
                                .similarityThreshold(0.0)
                                .build();
                List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

                if (relevantDocs.isEmpty()) {
                        log.warn("No relevant documents found for: {}", question);
                        return chatClient.prompt()
                                        .user(question)
                                        .call()
                                        .content();
                }

                String context = relevantDocs.stream()
                                .map(Document::getText)
                                .collect(Collectors.joining("\n\n---\n\n"));

                log.debug("Retrieved {} docs, context length={}", relevantDocs.size(), context.length());

                String augmentedPrompt = "Answer the question based on the provided Context.\n"
                                + "If the answer is not in the Context, say you could not find the information.\n\n"
                                + "Context:\n" + context + "\n\n"
                                + "Question: " + question + "\n\n"
                                + "Answer:";

                return chatClient.prompt()
                                .user(augmentedPrompt)
                                .call()
                                .content();
        }

        public String ragQuery(String question) {
                return ragQuery(question, 3);
        }

        // =========================================================
        // 3. RAG WITH RetrievalAugmentationAdvisor (Spring AI 1.0.0)
        // =========================================================

        /**
         * RAG using Spring AI 1.0.0 RetrievalAugmentationAdvisor.
         * Replaces old QuestionAnswerAdvisor.
         */
        public String ragWithAdvisor(String question, int topK) {
                log.debug("RAG with RetrievalAugmentationAdvisor: {}", question);

                VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                                .vectorStore(vectorStore)
                                .topK(topK)
                                .similarityThreshold(0.0)
                                .build();

                RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                                .documentRetriever(retriever)
                                .build();

                return chatClient.prompt()
                                .user(question)
                                .advisors(ragAdvisor)
                                .call()
                                .content();
        }

        /**
         * RAG + Memory: combine vector search + conversation history.
         */
        public String ragWithMemory(String conversationId, String question) {
                log.debug("RAG+Memory [{}]: {}", conversationId, question);

                VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                                .vectorStore(vectorStore)
                                .topK(3)
                                .build();

                RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                                .documentRetriever(retriever)
                                .build();

                return chatClient.prompt()
                                .user(question)
                                .advisors(
                                                ragAdvisor,
                                                MessageChatMemoryAdvisor.builder(chatMemory)
                                                                .conversationId(conversationId)
                                                                .build())
                                .call()
                                .content();
        }

        // =========================================================
        // 4. RETRIEVE ONLY (no generation)
        // =========================================================

        public List<Document> retrieveDocuments(String query, int topK) {
                SearchRequest request = SearchRequest.builder()
                                .query(query)
                                .topK(topK)
                                .build();
                return vectorStore.similaritySearch(request);
        }

        public List<String> retrieveTexts(String query, int topK) {
                return retrieveDocuments(query, topK).stream()
                                .map(Document::getText)
                                .collect(Collectors.toList());
        }

        // =========================================================
        // [PgVector] 5. DOCUMENT MANAGEMENT (PgVector persistent ops)
        // =========================================================

        /**
         * [PgVector] Xóa documents khỏi PgVectorStore theo danh sách ID.
         * Khác SimpleVectorStore (in-memory), PgVector lưu vĩnh viễn
         * nên cần API xóa để dọn dữ liệu cũ.
         *
         * @param ids Danh sách UUID (String) của documents cần xóa
         */
        public void deleteDocuments(List<String> ids) {
                log.info("[PgVector] Deleting {} documents by ID", ids.size());
                vectorStore.delete(ids);
                log.debug("[PgVector] Deleted document IDs: {}", ids);
        }

        /**
         * [PgVector] Xóa toàn bộ documents trong PgVectorStore bằng cách
         * tìm tất cả documents và xóa theo ID (vì PgVectorStore chưa có API clear()).
         *
         * ⚠️ THẬN TRỌNG: Hành động này không thể hoàn tác.
         */
        public void deleteAllDocuments() {
                log.warn("[PgVector] Deleting ALL documents from PgVectorStore!");
                // Tìm 10000 docs bất kỳ rồi xóa (threshold=0 để lấy tất cả)
                List<Document> all = vectorStore.similaritySearch(
                                SearchRequest.builder()
                                                .query("*")
                                                .topK(10000)
                                                .similarityThreshold(0.0)
                                                .build());
                if (!all.isEmpty()) {
                        List<String> ids = all.stream()
                                        .map(Document::getId)
                                        .collect(Collectors.toList());
                        vectorStore.delete(ids);
                        log.info("[PgVector] Deleted {} documents total", ids.size());
                } else {
                        log.info("[PgVector] No documents to delete");
                }
        }

        /**
         * [PgVector] Đếm số documents trong PgVectorStore
         * bằng cách tìm kiếm với topK lớn.
         *
         * @return Số lượng documents hiện có (approximate)
         */
        public long countDocuments() {
                List<Document> docs = vectorStore.similaritySearch(
                                SearchRequest.builder()
                                                .query("*")
                                                .topK(10000)
                                                .similarityThreshold(0.0)
                                                .build());
                long count = docs.size();
                log.debug("[PgVector] Document count: {}", count);
                return count;
        }

        /**
         * [PgVector] Retrieve documents với metadata filter.
         * Lọc theo metadata trước, rồi compute similarity.
         *
         * @param query            Câu truy vấn
         * @param topK             Số kết quả tối đa
         * @param filterExpression Biểu thức lọc metadata, ví dụ: "source ==
         *                         'input.txt'"
         */
        public List<Document> retrieveDocumentsWithFilter(String query, int topK, String filterExpression) {
                log.debug("[PgVector] RetrieveWithFilter: filter='{}', query='{}'", filterExpression, query);
                return vectorStore.similaritySearch(
                                SearchRequest.builder()
                                                .query(query)
                                                .topK(topK)
                                                .filterExpression(filterExpression) // [PgVector] metadata filter
                                                .build());
        }
}
