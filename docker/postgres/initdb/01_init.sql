-- ============================================================
-- knowledge-agent 建表脚本（智能客服版）
-- 挂载于 docker/postgres/initdb，PostgreSQL 容器首次初始化时自动执行。
-- 与 drawio「数据库设计」页保持一致；Flyway 已移除，结构变更直接改本文件。
-- ============================================================

-- pgvector 扩展：knowledge_chunk.embedding 的 vector 类型与 <=> 距离算子依赖此扩展。
CREATE EXTENSION IF NOT EXISTS vector;

-- ------------------------------------------------------------
-- 会话表：一行一场会话。
-- 水位线 = summarized_until_id：id <= 该值的消息已压缩进 summary，不再进入上下文原文；
-- id > 该值的消息原文保留。水位线只进不退，消息永不删除。
-- update_time 仅负责会话列表排序与活跃刷新，禁止充当水位线。
-- processing_started_at / processing_completed_at 为同一会话并发保护：
-- started > completed 表示有一轮对话在途；发起超过僵尸超时仍未完成视为失效锁，可被下一轮接管。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversation (
  id                     BIGINT       PRIMARY KEY,
  title                  VARCHAR(255),
  create_time            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  update_time            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  summary                TEXT,
  summarized_until_id    BIGINT,
  summary_tokens         INTEGER,
  processing_started_at  TIMESTAMPTZ,
  processing_completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_conversation_update
  ON conversation (update_time DESC);

-- ------------------------------------------------------------
-- 消息表：一行一轮（用户消息与AI回复同存一行）。
-- user_tokens / ai_tokens 写入时落库，预算计算时直接累加，不重算。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message (
  id              BIGINT      PRIMARY KEY,
  conversation_id BIGINT      NOT NULL,
  user_message    TEXT        NOT NULL,
  ai_message      TEXT,
  create_time     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  user_tokens     INTEGER     NOT NULL DEFAULT 0,
  ai_tokens       INTEGER     NOT NULL DEFAULT 0
);

-- 会话内按雪花ID正序拉取，直接支撑水位线边界查询
-- （WHERE conversation_id = ? AND id > #{水位线} ORDER BY id）。
CREATE INDEX IF NOT EXISTS idx_message_conversation_id
  ON message (conversation_id, id);

-- ------------------------------------------------------------
-- 知识文件表：文档入库元信息。
-- kb_name：所属知识库（自描述命名，如：退货规则原文库 / 退货规则问答库），
--          Agent 工具 queryKnowledgeBases 按 kb_name 聚合列出，供大模型选择。
-- document_type 不设数据库 CHECK，由应用层枚举管控（RAW_DOC / QA_SET）。
-- source：素材来源（jd / taobao / gov / constructed 等），供检索结果溯源。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_file (
  id              BIGINT       PRIMARY KEY,
  kb_name         VARCHAR(64)  NOT NULL,
  title           VARCHAR(255) NOT NULL,
  document_type   VARCHAR(32)  NOT NULL,
  source          VARCHAR(32),
  file_format     VARCHAR(16)  NOT NULL,
  file_size       BIGINT       NOT NULL,
  file_location   VARCHAR(512) NOT NULL,
  chunk_count     INTEGER      NOT NULL,
  embedding_model VARCHAR(64)  NOT NULL,
  create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_knowledge_file_kb
  ON knowledge_file (kb_name, create_time DESC);

-- ------------------------------------------------------------
-- 知识切片表：切块内容与向量。
-- embedding 维度 1024 与 BGE-M3 配置一致，更换向量模型需同步修改本列维度并全量重建。
-- metadata 存标签（如 {"docType":"QA_SET","source":"jd"}），JSONB + GIN 支撑按标签过滤。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_chunk (
  id          BIGINT        PRIMARY KEY,
  file_id     BIGINT        NOT NULL,
  chunk_index INTEGER       NOT NULL,
  content     TEXT          NOT NULL,
  embedding   vector(1024)  NOT NULL,
  text_length INTEGER       NOT NULL,
  token_count INTEGER       NOT NULL,
  metadata    JSONB,
  create_time TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_knowledge_chunk_file_index UNIQUE (file_id, chunk_index)
);

-- 余弦距离 HNSW 索引，支撑 <=> 相似度检索。
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_embedding
  ON knowledge_chunk USING hnsw (embedding vector_cosine_ops);

-- metadata 标签过滤索引，支撑 @> 包含查询。
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_metadata
  ON knowledge_chunk USING gin (metadata jsonb_path_ops);

-- ------------------------------------------------------------
-- 系统日志表：operation_detail 由 SystemLogAspect 序列化为 JSON 后写入。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS system_log (
  id                BIGINT       PRIMARY KEY,
  request_id        VARCHAR(64)  NOT NULL,
  operation_type    VARCHAR(64)  NOT NULL,
  operation_detail  JSONB,
  prompt_tokens     INTEGER,
  completion_tokens INTEGER,
  model_name        VARCHAR(64),
  status            VARCHAR(16)  NOT NULL,
  error_code        VARCHAR(64),
  error_reason      TEXT,
  latency_ms        BIGINT,
  create_time       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_system_log_status CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_system_log_request
  ON system_log (request_id);

CREATE INDEX IF NOT EXISTS idx_system_log_op_time
  ON system_log (operation_type, create_time DESC);
