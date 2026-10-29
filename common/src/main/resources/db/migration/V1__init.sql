CREATE TABLE accounts (
  account_id BINARY(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  eligibility_level VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
  display_name VARCHAR(160),
  version BIGINT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (account_id),
  CONSTRAINT ck_account_status CHECK (status IN ('NORMAL','FROZEN','PENDING_VERIFICATION','DELETED'))
) ENGINE=InnoDB;

CREATE TABLE categories (
  category_id BINARY(16) NOT NULL,
  name VARCHAR(120) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (category_id), UNIQUE KEY uk_category_name (name),
  CONSTRAINT ck_category_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE catalog_items (
  item_id BINARY(16) NOT NULL,
  type VARCHAR(24) NOT NULL,
  title VARCHAR(512) NOT NULL,
  creator VARCHAR(512),
  category_id BINARY(16) NOT NULL,
  subject_keywords JSON,
  description TEXT,
  publisher VARCHAR(255),
  publication_date DATE,
  language_code VARCHAR(16),
  identifier_type VARCHAR(24),
  identifier_value VARCHAR(128),
  metadata JSON,
  cover_url VARCHAR(1024),
  search_text VARCHAR(2048) NOT NULL,
  shelf_status VARCHAR(32) NOT NULL,
  total_copies INT UNSIGNED NOT NULL DEFAULT 0,
  available_copies INT UNSIGNED NOT NULL DEFAULT 0,
  published_at DATETIME(6),
  version BIGINT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (item_id),
  KEY ix_item_category_status (category_id, shelf_status, item_id),
  KEY ix_item_status_published (shelf_status, published_at, title(100), item_id),
  CONSTRAINT fk_item_category FOREIGN KEY (category_id) REFERENCES categories(category_id),
  CONSTRAINT ck_item_type CHECK (type IN ('EBOOK','PERIODICAL')),
  CONSTRAINT ck_item_shelf CHECK (shelf_status IN ('ON_SHELF','OFF_SHELF','REMOVED')),
  CONSTRAINT ck_item_copies CHECK (available_copies <= total_copies)
) ENGINE=InnoDB;

CREATE TABLE loans (
  loan_id BINARY(16) NOT NULL,
  account_id BINARY(16) NOT NULL,
  item_id BINARY(16) NOT NULL,
  status VARCHAR(24) NOT NULL,
  borrowed_at DATETIME(6) NOT NULL,
  due_at DATETIME(6) NOT NULL,
  returned_at DATETIME(6),
  active_account_id BINARY(16) GENERATED ALWAYS AS (IF(returned_at IS NULL, account_id, NULL)) STORED,
  title_snapshot VARCHAR(512) NOT NULL,
  creator_snapshot VARCHAR(512),
  type_snapshot VARCHAR(32) NOT NULL,
  policy_version VARCHAR(64) NOT NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 1,
  PRIMARY KEY (loan_id),
  UNIQUE KEY uk_active_account_item (active_account_id, item_id),
  KEY ix_account_due (account_id, returned_at, due_at, loan_id),
  KEY ix_account_returned (account_id, returned_at, loan_id),
  CONSTRAINT ck_loan_time CHECK (due_at > borrowed_at),
  CONSTRAINT ck_loan_state CHECK ((returned_at IS NULL AND status = 'ACTIVE') OR (returned_at IS NOT NULL AND status IN ('RETURNED','RETURNED_LATE')))
) ENGINE=InnoDB;

CREATE TABLE idempotency_records (
  account_id BINARY(16) NOT NULL,
  operation VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash BINARY(32) NOT NULL,
  http_status SMALLINT UNSIGNED NOT NULL,
  response_json JSON NOT NULL,
  created_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  PRIMARY KEY (account_id, operation, idempotency_key),
  KEY ix_idem_expiry (expires_at)
) ENGINE=InnoDB;
