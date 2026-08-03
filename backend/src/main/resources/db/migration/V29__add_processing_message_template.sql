-- Migration: V26__add_processing_message_template.sql
-- Description: Adds PROCESSING message template for 30-second response timeout
-- Requirement: 11.2 - THE Workflow_Engine SHALL respond to every WhatsApp message within 30 seconds.
--              If processing takes longer, send a "processing" message immediately and follow up with the real response.

-- PROCESSING: Message sent when response takes longer than 30 seconds (English)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('processing', 
    'One moment {fatherName}, I''m working on your request... 🔄',
    'en');

-- PROCESSING: Message sent when response takes longer than 30 seconds (Hebrew)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('processing', 
    'רגע {fatherName}, אני מעבד את הבקשה שלך... 🔄',
    'he');

-- Table documentation update
COMMENT ON COLUMN message_templates.message_type IS 'Unique identifier for the message type (e.g., welcome_greeting, schedule_confirm, processing)';

