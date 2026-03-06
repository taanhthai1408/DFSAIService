-- ================================================================
-- PgVector Init Script cho Spring AI
-- Chạy script này trên PostgreSQL server (10.30.1.200:5432)
-- với quyền superuser trước khi khởi động ứng dụng
-- ================================================================

-- 1. Enable pgvector extension (cần quyền superuser)
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Tạo bảng vector_store
--    Dimension = 4096 (llama3 embedding size).
--    ⚠️ HNSW/IVFFlat index CHỈ hỗ trợ tối đa 2000 dims
--       → Dùng exact search (không index) cho llama3
--       → Nếu muốn HNSW: dùng model nomic-embed-text (768 dims)
CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(4096)
);

-- 3. KHÔNG tạo HNSW index vì llama3 = 4096 dims > giới hạn 2000 dims
-- Exact search (linear scan) sẽ được dùng thay thế.
-- Nếu dùng nomic-embed-text (768 dims), dùng lệnh sau:
-- CREATE INDEX vector_store_embedding_idx
--     ON vector_store USING hnsw (embedding vector_cosine_ops);

-- 4. Kiểm tra kết quả
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_name = 'vector_store';

