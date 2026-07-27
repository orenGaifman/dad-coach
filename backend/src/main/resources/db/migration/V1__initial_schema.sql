CREATE TABLE father (
  id BIGSERIAL PRIMARY KEY,
  phone VARCHAR(32) NOT NULL UNIQUE,
  display_name VARCHAR(120),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE conversation_message (
  id BIGSERIAL PRIMARY KEY,
  father_id BIGINT REFERENCES father(id),
  direction VARCHAR(16) NOT NULL,
  content TEXT NOT NULL,
  provider_message_id VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_conversation_message_father_created ON conversation_message(father_id, created_at);
