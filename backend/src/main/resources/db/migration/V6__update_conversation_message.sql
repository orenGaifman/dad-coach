-- Link conversation_message to conversation entity
ALTER TABLE conversation_message
    ADD COLUMN conversation_id BIGINT REFERENCES conversation(id),
    ADD COLUMN role VARCHAR(20) DEFAULT 'USER';
