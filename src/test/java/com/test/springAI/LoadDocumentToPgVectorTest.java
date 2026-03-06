package com.test.springAI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ===========================================================
 * LoadDocumentToPgVectorTest
 * ===========================================================
 *
 * Integration test: Đọc file input.txt, split thành chunks,
 * embed bằng Ollama và lưu vào PgVectorStore.
 *
 * ⚠️ YÊU CẦU:
 * - PostgreSQL (10.30.1.200:5432) phải đang chạy và có pgvector extension
 * - Ollama (103.163.216.181:11434) phải đang chạy với model llama3
 * - Bảng vector_store đã được tạo (hoặc initialize-schema: true)
 *
 * Chạy test:
 * mvn test -Dtest=LoadDocumentToPgVectorTest -pl .
 */
@SpringBootTest
@DisplayName("Load input.txt → PgVector Integration Test")
class LoadDocumentToPgVectorTest {

    @Value("classpath:input.txt")
    private Resource inputResource;

    @Autowired
    private VectorStore vectorStore;

    // =========================================================
    // TEST 1: Load toàn bộ file vào PgVector (mỗi dòng 1 document)
    // =========================================================

    @Test
    @DisplayName("Load từng dòng của input.txt vào PgVector")
    void loadLineByLine() throws IOException {
        System.out.println("=== BẮT ĐẦU LOAD input.txt THEO TỪNG DÒNG ===");

        List<Document> documents = Files.lines(
                inputResource.getFile().toPath(), StandardCharsets.UTF_8)
                .filter(line -> !line.isBlank()) // Bỏ qua dòng trống
                .map(Document::new)
                .collect(Collectors.toList());

        System.out.printf("Đọc được %d dòng từ input.txt%n", documents.size());

        vectorStore.add(documents);

        System.out.printf("✅ Đã thêm %d documents vào PgVector%n", documents.size());
    }

    // =========================================================
    // TEST 2: Load file với TokenTextSplitter (chia nhỏ chunks)
    // =========================================================

    @Test
    @DisplayName("Load input.txt với TokenTextSplitter vào PgVector")
    void loadWithTokenSplitter() throws IOException {
        System.out.println("=== BẮT ĐẦU LOAD input.txt VỚI TOKEN SPLITTER ===");

        // Đọc toàn bộ file thành 1 document
        String fullText = Files.readString(
                inputResource.getFile().toPath(), StandardCharsets.UTF_8);

        Document fullDocument = new Document(fullText);

        // Split thành các chunks nhỏ hơn
        TokenTextSplitter splitter = new TokenTextSplitter(
                200, // defaultChunkSize: mỗi chunk ~200 tokens
                50, // minChunkSizeChars: tối thiểu 50 ký tự
                5, // minChunkLengthToEmbed: ít nhất 5 tokens
                10, // maxNumChunks: max 10 chunks
                true // keepSeparator
        );

        List<Document> chunks = splitter.split(fullDocument);
        System.out.printf("Chia thành %d chunks từ file%n", chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            String previewText = chunk.getText();
            String preview = (previewText != null)
                    ? previewText.substring(0, Math.min(60, previewText.length()))
                    : "(no text)";
            System.out.printf("  Chunk %d (len=%d): %s...%n",
                    i + 1,
                    previewText != null ? previewText.length() : 0,
                    preview);
        }

        vectorStore.add(chunks);
        System.out.printf("✅ Đã thêm %d chunks vào PgVector%n", chunks.size());
    }

    // =========================================================
    // TEST 3: Verify dữ liệu đã load (semantic search)
    // =========================================================

    @Test
    @DisplayName("Verify dữ liệu trong PgVector bằng semantic search")
    void verifyDataInPgVector() {
        System.out.println("=== KIỂM TRA DỮ LIỆU TRONG PGVECTOR ===");

        String[] queries = {
                "Spring AI là gì?",
                "PgVector lưu trữ như thế nào?",
                "RAG là gì?",
                "Ollama và LLM"
        };

        for (String query : queries) {
            System.out.printf("%nQuery: \"%s\"%n", query);

            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(2)
                            .similarityThreshold(0.0)
                            .build());

            if (results.isEmpty()) {
                System.out.println("  ⚠️  Không tìm thấy kết quả (DB có thể chưa có data)");
            } else {
                results.forEach(doc -> {
                    String t = doc.getText();
                    String preview = (t != null) ? t.substring(0, Math.min(80, t.length())) : "(no text)";
                    System.out.printf("  ✅ Score match: %s...%n", preview);
                });

            }
        }
    }
}
