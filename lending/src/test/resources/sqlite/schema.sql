-- SQLite 测试 schema（与 V1__init.sql 语义一致）
-- UUID 用 BLOB(16) 存储（与 UuidTypeHandler 的 setBytes/getBytes 匹配）
-- JSON 用 TEXT 存储（MyBatis 测试 Mapper 直接读写字符串）

CREATE TABLE IF NOT EXISTS accounts (
  account_id BLOB(16) NOT NULL PRIMARY KEY,
  status VARCHAR(32) NOT NULL,
  eligibility_level VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
  display_name VARCHAR(160),
  version INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS categories (
  category_id BLOB(16) NOT NULL PRIMARY KEY,
  name VARCHAR(120) NOT NULL UNIQUE,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS catalog_items (
  item_id BLOB(16) NOT NULL PRIMARY KEY,
  type VARCHAR(24) NOT NULL,
  title VARCHAR(512) NOT NULL,
  creator VARCHAR(512),
  category_id BLOB(16) NOT NULL,
  subject_keywords TEXT,
  description TEXT,
  publisher VARCHAR(255),
  publication_date TEXT,
  language_code VARCHAR(16),
  identifier_type VARCHAR(24),
  identifier_value VARCHAR(128),
  metadata TEXT,
  cover_url VARCHAR(1024),
  search_text VARCHAR(2048) NOT NULL,
  shelf_status VARCHAR(32) NOT NULL,
  total_copies INTEGER NOT NULL DEFAULT 0,
  available_copies INTEGER NOT NULL DEFAULT 0,
  published_at TEXT,
  version INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY (category_id) REFERENCES categories(category_id)
);

CREATE INDEX IF NOT EXISTS ix_item_category_status ON catalog_items (category_id, shelf_status, item_id);
CREATE INDEX IF NOT EXISTS ix_item_status_published ON catalog_items (shelf_status, published_at, title, item_id);

CREATE TABLE IF NOT EXISTS loans (
  loan_id BLOB(16) NOT NULL PRIMARY KEY,
  account_id BLOB(16) NOT NULL,
  item_id BLOB(16) NOT NULL,
  status VARCHAR(24) NOT NULL,
  borrowed_at TEXT NOT NULL,
  due_at TEXT NOT NULL,
  returned_at TEXT,
  title_snapshot VARCHAR(512) NOT NULL,
  creator_snapshot VARCHAR(512),
  type_snapshot VARCHAR(32) NOT NULL,
  policy_version VARCHAR(64) NOT NULL,
  version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS ix_loan_account_item ON loans (account_id, item_id);
CREATE INDEX IF NOT EXISTS ix_loan_account_due ON loans (account_id, returned_at, due_at, loan_id);

CREATE TABLE IF NOT EXISTS idempotency_records (
  account_id BLOB(16) NOT NULL,
  operation VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash BLOB(32) NOT NULL,
  http_status INTEGER NOT NULL,
  response_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  PRIMARY KEY (account_id, operation, idempotency_key)
);

CREATE INDEX IF NOT EXISTS ix_idem_expiry ON idempotency_records (expires_at);