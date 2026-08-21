-- V5: Fix message_templates unique constraint and seed fallback messages
-- The original schema had UNIQUE on message_type only, but we need 
-- UNIQUE on (message_type, language) to support multiple languages

-- Step 1: Drop the old unique constraint
ALTER TABLE message_templates DROP CONSTRAINT IF EXISTS message_templates_message_type_key;

-- Step 2: Add correct unique constraint on (message_type, language)
ALTER TABLE message_templates ADD CONSTRAINT message_templates_type_lang_key UNIQUE (message_type, language);

-- Step 3: Seed message templates for all MessageType values
-- Each message type needs both Hebrew (he) and English (en) templates

-- WELCOME_GREETING
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'welcome_greeting', 'שלום {fatherName}! ברוך הבא ל-Dad Coach. אני כאן לעזור לך לבנות הרגל של זמן איכות עם הילדים שלך. 👨‍👧 מוכן להתחיל?', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'welcome_greeting', 'Hi {fatherName}! Welcome to Dad Coach. I''m here to help you build a habit of quality time with your kids. 👨‍👧 Ready to get started?', 'en', true, NOW(), NOW());

-- WELCOME_EXPLAIN
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'welcome_explain', 'Dad Coach עוזר לך לתכנן ולעקוב אחרי זמן איכות עם הילדים שלך. תזמן מפגשים, השלם אותם, וצבור התקדמות במערכת החגורות שלנו. פשוט וקל! מוכן לתאם את המפגש הראשון שלך?', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'welcome_explain', 'Dad Coach helps you plan and track quality time with your kids. Schedule sessions, complete them, and earn progress in our belt system. Simple and easy! Ready to schedule your first session?', 'en', true, NOW(), NOW());

-- SCHEDULE_SLOTS
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'schedule_slots', 'בחר זמן לזמן איכות עם {childName}:', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'schedule_slots', 'Choose a time for Quality Time with {childName}:', 'en', true, NOW(), NOW());

-- SCHEDULE_CONFIRM
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'schedule_confirm', 'מעולה! זמן איכות עם {childName} נקבע ל-{time}. תהנו! 💪', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'schedule_confirm', 'Great! Quality Time with {childName} is scheduled for {time}. Enjoy! 💪', 'en', true, NOW(), NOW());

-- SCHEDULE_NO_SLOTS
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'schedule_no_slots', 'לא מצאתי זמנים פנויים ביומן שלך לשבוע הקרוב. אנא בדוק את היומן שלך או נסה שוב מאוחר יותר.', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'schedule_no_slots', 'I couldn''t find any available slots in your calendar for the next week. Please check your calendar or try again later.', 'en', true, NOW(), NOW());

-- WAITING_REMINDER
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'waiting_reminder', 'בוקר טוב {fatherName}! זמן איכות עם {childName} היום ב-{time}. תהנו! 💪', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'waiting_reminder', 'Good morning {fatherName}! Quality Time with {childName} today at {time}. Have a great time! 💪', 'en', true, NOW(), NOW());

-- WAITING_SCHEDULE_INFO
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'waiting_schedule_info', 'זמן האיכות הבא שלך עם {childName} הוא ב-{time}.', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'waiting_schedule_info', 'Your next Quality Time with {childName} is at {time}.', 'en', true, NOW(), NOW());

-- FOLLOW_UP_QUESTION
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'follow_up_question', 'השלמת את זמן האיכות עם {childName}?', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'follow_up_question', 'Did you complete your Quality Time with {childName}?', 'en', true, NOW(), NOW());

-- FOLLOW_UP_COMPLETED
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'follow_up_completed', 'כל הכבוד {fatherName}! 🎉 הרצף שלך עכשיו {streak}. המשך כך!', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'follow_up_completed', 'Awesome {fatherName}! 🎉 Your streak is now {streak}. Keep it up!', 'en', true, NOW(), NOW());

-- FOLLOW_UP_MISSED
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'follow_up_missed', 'לא נורא {fatherName}, יש עוד הזדמנויות. בוא נתאם זמן איכות חדש.', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'follow_up_missed', 'No worries {fatherName}, there will be more opportunities. Let''s schedule another Quality Time.', 'en', true, NOW(), NOW());

-- ACTIVITY_IDEAS
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'activity_ideas', 'הנה כמה רעיונות לזמן איכות עם {childName}:', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'activity_ideas', 'Here are some ideas for Quality Time with {childName}:', 'en', true, NOW(), NOW());

-- DASHBOARD_SUMMARY
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'dashboard_summary', '📊 ההתקדמות שלך:
🥋 חגורה: {belt}
🔥 רצף: {streak}
✅ סה"כ מפגשים: {qualityTimeCount}

לצפייה בדשבורד המלא: {dashboardUrl}', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'dashboard_summary', '📊 Your progress:
🥋 Belt: {belt}
🔥 Streak: {streak}
✅ Total sessions: {qualityTimeCount}

View full dashboard: {dashboardUrl}', 'en', true, NOW(), NOW());

-- CLARIFICATION
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'clarification', 'לא הבנתי את התשובה. אנא בחר אחת מהאפשרויות.', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'clarification', 'I didn''t understand that. Please choose one of the options.', 'en', true, NOW(), NOW());

-- ERROR_GENERIC
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'error_generic', 'מצטער, משהו השתבש. אנא נסה שוב.', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'error_generic', 'Sorry, something went wrong. Please try again.', 'en', true, NOW(), NOW());

-- PROCESSING
INSERT INTO message_templates (id, message_type, template_text, language, active, created_at, updated_at) VALUES
(gen_random_uuid(), 'processing', 'רגע {fatherName}, אני מעבד את הבקשה שלך... 🔄', 'he', true, NOW(), NOW()),
(gen_random_uuid(), 'processing', 'One moment {fatherName}, I''m processing your request... 🔄', 'en', true, NOW(), NOW());
