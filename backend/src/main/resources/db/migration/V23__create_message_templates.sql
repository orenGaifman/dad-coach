-- Migration: V23__create_message_templates.sql
-- Description: Creates message_templates table with default English and Hebrew fallback templates
-- Requirement: 10.4 - Every message type SHALL have a corresponding fallback template
-- Note: Dad Coach supports ONLY English (en) and Hebrew (he) - NO Spanish

-- Create message_templates table
CREATE TABLE message_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_type    VARCHAR(50) NOT NULL,
    template_text   TEXT NOT NULL,
    language        VARCHAR(10) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_templates_type_lang UNIQUE (message_type, language),
    CONSTRAINT chk_message_templates_lang CHECK (language IN ('en', 'he'))
);

-- Create indexes for faster lookups
CREATE INDEX idx_message_templates_type ON message_templates(message_type);
CREATE INDEX idx_message_templates_active ON message_templates(active) WHERE active = TRUE;
CREATE INDEX idx_message_templates_type_lang ON message_templates(message_type, language);

-- =============================================================================
-- ENGLISH TEMPLATES (en)
-- =============================================================================

-- WELCOME_GREETING: Initial greeting for new fathers
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('WELCOME_GREETING', 
    'Hi {father_name}! 👋 Welcome to Dad Coach. I''m here to help you strengthen your relationship with {child_name} through quality moments together.',
    'en');

-- WELCOME_EXPLAIN: Explains how Dad Coach works
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('WELCOME_EXPLAIN',
    'Dad Coach is simple: you schedule Quality Time with your child, complete it, and earn belt progression based on your progress. 🥋 Ready to schedule your first Quality Time?',
    'en');

-- SCHEDULE_SLOTS: Presents available time slots for Quality Time
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('SCHEDULE_SLOTS',
    '📅 Here are the available times for Quality Time with {child_name}:\n\n{time_slots}\n\nReply with the number of the time you prefer, or type "other" for more options.',
    'en');

-- SCHEDULE_CONFIRM: Confirms a scheduled Quality Time event
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('SCHEDULE_CONFIRM',
    '✅ Perfect! Your Quality Time with {child_name} is scheduled for {scheduled_time}. I''ll send you a reminder on the day. Enjoy your moment together! 💪',
    'en');

-- WAITING_REMINDER: Morning reminder on the day of Quality Time
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('WAITING_REMINDER',
    'Good morning {father_name}! ☀️ Today you have Quality Time with {child_name} at {scheduled_time}. Have a great moment together! 💪',
    'en');

-- FOLLOW_UP_QUESTION: Asks if father completed Quality Time
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('FOLLOW_UP_QUESTION',
    'Did you complete your Quality Time with {child_name}? 🤔\n\nReply "Yes" or "No".',
    'en');

-- FOLLOW_UP_COMPLETED: Acknowledges completion and celebrates
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('FOLLOW_UP_COMPLETED',
    '🎉 Great job, {father_name}! Your current streak is {streak_count} consecutive Quality Times. {belt_message} Ready to schedule the next one?',
    'en');

-- FOLLOW_UP_MISSED: Encouraging message when Quality Time was not completed
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('FOLLOW_UP_MISSED',
    'Don''t worry, {father_name}. Sometimes things don''t go as planned. 💙 What matters is that you''re here. Want to schedule another Quality Time?',
    'en');

-- ACTIVITY_IDEAS: Presents activity suggestions
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('ACTIVITY_IDEAS',
    '💡 Here are some ideas for your Quality Time with {child_name}:\n\n{activity_list}\n\nReply with the activity number for more details, "more" for other ideas, or "thanks" when you''re done.',
    'en');

-- DASHBOARD_SUMMARY: Text summary of dashboard stats for WhatsApp
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('DASHBOARD_SUMMARY',
    '📊 Your progress, {father_name}:\n\n🥋 Current belt: {current_belt}\n🔥 Current streak: {streak_count}\n✅ Quality Times completed: {total_completed}\n🎯 Next goal: {next_milestone}\n\nSee your full dashboard here: {dashboard_link}',
    'en');

-- CLARIFICATION: When user message doesn't match expected patterns
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('CLARIFICATION',
    'I didn''t understand your message. Please choose one of these options:\n\n{options}',
    'en');

-- ERROR_GENERIC: Generic error message for system failures
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('ERROR_GENERIC',
    'Sorry, I had a problem processing your message. Please try again in a few moments. 🙏',
    'en');

-- =============================================================================
-- HEBREW TEMPLATES (he)
-- =============================================================================

-- WELCOME_GREETING: Initial greeting for new fathers (Hebrew)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('WELCOME_GREETING', 
    'היי {father_name}! 👋 ברוך הבא ל-Dad Coach. אני כאן כדי לעזור לך לחזק את הקשר עם {child_name} דרך רגעים איכותיים יחד.',
    'he');

-- WELCOME_EXPLAIN: Explains how Dad Coach works (Hebrew)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('WELCOME_EXPLAIN',
    'Dad Coach פשוט: אתה קובע זמן איכות עם הילד שלך, מבצע אותו, וצובר התקדמות בחגורות על פי ההתקדמות שלך. 🥋 מוכן לקבוע את זמן האיכות הראשון שלך?',
    'he');

-- SCHEDULE_SLOTS: Presents available time slots for Quality Time (Hebrew)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('SCHEDULE_SLOTS',
    '📅 הנה הזמנים הפנויים לזמן איכות עם {child_name}:\n\n{time_slots}\n\nענה עם המספר של הזמן שאתה מעדיף, או כתוב "אחר" לאפשרויות נוספות.',
    'he');

-- SCHEDULE_CONFIRM: Confirms a scheduled Quality Time event (Hebrew)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('SCHEDULE_CONFIRM',
    '✅ מעולה! זמן האיכות שלך עם {child_name} נקבע ל-{scheduled_time}. אשלח לך תזכורת ביום עצמו. תהנו מהרגע יחד! 💪',
    'he');

-- WAITING_REMINDER: Morning reminder on the day of Quality Time (Hebrew)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('WAITING_REMINDER',
    'בוקר טוב {father_name}! ☀️ היום יש לך זמן איכות עם {child_name} ב-{scheduled_time}. שיהיה לכם רגע נהדר יחד! 💪',
    'he');

-- FOLLOW_UP_QUESTION: Asks if father completed Quality Time (Hebrew)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('FOLLOW_UP_QUESTION',
    'האם השלמת את זמן האיכות עם {child_name}? 🤔\n\nענה "כן" או "לא".',
    'he');

-- FOLLOW_UP_COMPLETED: Acknowledges completion and celebrates (Hebrew)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('FOLLOW_UP_COMPLETED',
    '🎉 עבודה מצוינת, {father_name}! הרצף הנוכחי שלך הוא {streak_count} זמני איכות רצופים. {belt_message} מוכן לקבוע את הבא?',
    'he');

-- FOLLOW_UP_MISSED: Encouraging message when Quality Time was not completed (Hebrew)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('FOLLOW_UP_MISSED',
    'אל דאגה, {father_name}. לפעמים דברים לא הולכים כמתוכנן. 💙 מה שחשוב זה שאתה כאן. רוצה לקבוע זמן איכות נוסף?',
    'he');

-- ACTIVITY_IDEAS: Presents activity suggestions (Hebrew)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('ACTIVITY_IDEAS',
    '💡 הנה כמה רעיונות לזמן האיכות שלך עם {child_name}:\n\n{activity_list}\n\nענה עם מספר הפעילות לפרטים נוספים, "עוד" לרעיונות אחרים, או "תודה" כשסיימת.',
    'he');

-- DASHBOARD_SUMMARY: Text summary of dashboard stats for WhatsApp (Hebrew)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('DASHBOARD_SUMMARY',
    '📊 ההתקדמות שלך, {father_name}:\n\n🥋 חגורה נוכחית: {current_belt}\n🔥 רצף נוכחי: {streak_count}\n✅ זמני איכות שהושלמו: {total_completed}\n🎯 יעד הבא: {next_milestone}\n\nצפה בדשבורד המלא כאן: {dashboard_link}',
    'he');

-- CLARIFICATION: When user message doesn't match expected patterns (Hebrew)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('CLARIFICATION',
    'לא הבנתי את ההודעה שלך. אנא בחר אחת מהאפשרויות הבאות:\n\n{options}',
    'he');

-- ERROR_GENERIC: Generic error message for system failures (Hebrew)
INSERT INTO message_templates (message_type, template_text, language)
VALUES ('ERROR_GENERIC',
    'סליחה, הייתה בעיה בעיבוד ההודעה שלך. אנא נסה שוב בעוד כמה רגעים. 🙏',
    'he');

-- =============================================================================
-- Table documentation
-- =============================================================================
COMMENT ON TABLE message_templates IS 'Stores localized fallback message templates for the workflow engine. Templates support {placeholder} syntax for dynamic content substitution. Dad Coach supports English (en) and Hebrew (he) only.';
COMMENT ON COLUMN message_templates.message_type IS 'Unique identifier for the message type (e.g., WELCOME_GREETING, SCHEDULE_CONFIRM)';
COMMENT ON COLUMN message_templates.template_text IS 'The template text with {placeholder} syntax for variable substitution';
COMMENT ON COLUMN message_templates.language IS 'ISO language code: en (English) or he (Hebrew)';
COMMENT ON COLUMN message_templates.active IS 'Whether this template is currently active for use';
