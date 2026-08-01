-- V20: Weekly/Monthly Goals System and Flash Missions
-- Adds goal tracking and flash mission category for spontaneous quality time

-- ============================================================================
-- FATHER GOALS TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS father_goal (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    
    -- Goal period
    goal_type VARCHAR(20) NOT NULL, -- WEEKLY, MONTHLY
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    
    -- Target and progress (in minutes)
    target_minutes INTEGER NOT NULL,
    completed_minutes INTEGER NOT NULL DEFAULT 0,
    
    -- Goal state
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, COMPLETED, MISSED
    completed_at TIMESTAMP WITH TIME ZONE,
    
    -- Metadata
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT unique_father_goal_period UNIQUE (father_id, goal_type, period_start)
);

CREATE INDEX IF NOT EXISTS idx_father_goal_active 
    ON father_goal (father_id, status, period_end)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_father_goal_period 
    ON father_goal (father_id, goal_type, period_start, period_end);

-- ============================================================================
-- FLASH MISSIONS LIBRARY
-- ============================================================================

CREATE TABLE IF NOT EXISTS flash_mission_template (
    id BIGSERIAL PRIMARY KEY,
    
    -- Content
    title_he VARCHAR(200) NOT NULL,
    title_en VARCHAR(200) NOT NULL,
    description_he TEXT NOT NULL,
    description_en TEXT NOT NULL,
    
    -- Targeting
    min_age INTEGER, -- minimum child age in years
    max_age INTEGER, -- maximum child age in years
    context VARCHAR(30), -- HOME, CAR, OUTDOOR, ANYWHERE
    
    -- Timing
    estimated_minutes INTEGER NOT NULL DEFAULT 3,
    
    -- Metadata
    category VARCHAR(30) NOT NULL DEFAULT 'CONNECTION', -- CONNECTION, PLAY, TALK, PHYSICAL
    difficulty INTEGER NOT NULL DEFAULT 1, -- 1-3 (easy/medium/hard)
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_flash_mission_template_active 
    ON flash_mission_template (is_active, context, min_age, max_age)
    WHERE is_active = TRUE;

-- ============================================================================
-- FATHER SETTINGS FOR GOALS
-- ============================================================================

-- Add goal-related columns to father table
ALTER TABLE father
    ADD COLUMN IF NOT EXISTS weekly_goal_minutes INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN IF NOT EXISTS monthly_goal_minutes INTEGER NOT NULL DEFAULT 120,
    ADD COLUMN IF NOT EXISTS goals_started_at DATE,
    ADD COLUMN IF NOT EXISTS current_streak_weeks INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS longest_streak_weeks INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_quality_minutes INTEGER NOT NULL DEFAULT 0;

-- ============================================================================
-- SEED FLASH MISSIONS
-- ============================================================================

INSERT INTO flash_mission_template (title_he, title_en, description_he, description_en, min_age, max_age, context, estimated_minutes, category, difficulty) VALUES

-- CONNECTION missions (2-3 min)
('שאלה יומית', 'Daily Question', 
 'שאל את הילד: "מה הדבר הכי מצחיק שקרה לך היום?"', 
 'Ask your child: "What was the funniest thing that happened today?"',
 3, 18, 'ANYWHERE', 2, 'CONNECTION', 1),

('חיבוק ארוך', 'Long Hug',
 'תן לילד חיבוק ארוך של 20 שניות. בלי מילים, רק חיבוק.',
 'Give your child a 20-second hug. No words, just hugging.',
 2, 12, 'ANYWHERE', 1, 'CONNECTION', 1),

('סיפור מהיום שלך', 'Story from Your Day',
 'ספר לילד משהו מעניין או מצחיק שקרה לך היום בעבודה.',
 'Tell your child something interesting or funny that happened at work today.',
 4, 18, 'ANYWHERE', 3, 'CONNECTION', 1),

('מחמאה ספציפית', 'Specific Compliment',
 'תן לילד מחמאה ספציפית על משהו שעשה. לא "אתה מדהים" אלא "שמתי לב שעזרת לאחותך, זה היה מאוד נחמד".',
 'Give your child a specific compliment about something they did.',
 3, 18, 'ANYWHERE', 2, 'CONNECTION', 1),

('זיכרון משותף', 'Shared Memory',
 'ספר לילד על זיכרון יפה שלכם ביחד. "זוכר כשהלכנו ל..."',
 'Share a beautiful memory you have together.',
 4, 18, 'ANYWHERE', 3, 'CONNECTION', 2),

-- PLAY missions (3-5 min)
('אבן נייר ומספריים', 'Rock Paper Scissors',
 'משחק מהיר - מי מנצח 3 פעמים ראשון?',
 'Quick game - first to win 3 times?',
 4, 12, 'ANYWHERE', 3, 'PLAY', 1),

('בחירת שיר יחד', 'Choose a Song Together',
 'תנו לילד לבחור שיר להאזין יחד או לשיר ביחד.',
 'Let your child choose a song to listen to or sing together.',
 3, 16, 'CAR', 3, 'PLAY', 1),

('חידה מהירה', 'Quick Riddle',
 'שאל את הילד חידה או בדיחה. אפשר גם לבקש ממנו לספר לך אחת.',
 'Ask your child a riddle or joke. You can also ask them to tell you one.',
 5, 14, 'ANYWHERE', 2, 'PLAY', 1),

('משחק מילים', 'Word Game',
 'משחק מילים מהיר - מילה אחרונה, או מילים שמתחילות באות מסוימת.',
 'Quick word game - last letter or words starting with a certain letter.',
 6, 14, 'CAR', 4, 'PLAY', 2),

('דמיון מודרך', 'Guided Imagination',
 'שאל: "אם היית יכול לטוס לכל מקום בעולם עכשיו, לאן היית טס ומה היית עושה שם?"',
 'Ask: "If you could fly anywhere in the world right now, where would you go and what would you do?"',
 5, 14, 'ANYWHERE', 4, 'PLAY', 2),

-- TALK missions (3-5 min)
('מה חדש?', 'What''s New?',
 'שב עם הילד ושאל: "ספר לי משהו חדש שלמדת השבוע"',
 'Sit with your child and ask: "Tell me something new you learned this week"',
 5, 18, 'HOME', 4, 'TALK', 1),

('החבר הכי טוב', 'Best Friend',
 'שאל את הילד על החבר הכי טוב שלו. למה הוא החבר הכי טוב? מה הם אוהבים לעשות ביחד?',
 'Ask about their best friend. Why are they the best friend? What do they like doing together?',
 4, 14, 'ANYWHERE', 4, 'TALK', 1),

('חלומות', 'Dreams',
 'שאל: "מה אתה חולם להיות כשתהיה גדול? למה דווקא זה?"',
 'Ask: "What do you dream of being when you grow up? Why that?"',
 5, 16, 'ANYWHERE', 5, 'TALK', 2),

('היום בבית הספר', 'School Today',
 'במקום "מה היה?" שאל: "מה היה הדבר הכי משעמם היום?" או "עם מי שיחקת בהפסקה?"',
 'Instead of "how was it?" ask: "What was the most boring thing today?" or "Who did you play with at recess?"',
 6, 18, 'CAR', 3, 'TALK', 1),

-- PHYSICAL missions (2-5 min)
('קרב כריות', 'Pillow Fight',
 'קרב כריות מהיר! 2 דקות של טירוף ואחר כך שלום.',
 'Quick pillow fight! 2 minutes of craziness then peace.',
 3, 10, 'HOME', 3, 'PHYSICAL', 1),

('ריצה קצרה', 'Quick Race',
 'תציע לילד: "בוא נראה מי רץ יותר מהר עד הפינה וחזרה!"',
 'Challenge: "Let''s see who can run faster to the corner and back!"',
 4, 12, 'OUTDOOR', 3, 'PHYSICAL', 1),

('הרמה על הכתפיים', 'Shoulder Ride',
 'הרם את הילד על הכתפיים לסיבוב קצר בבית או בחוץ.',
 'Lift your child on your shoulders for a quick ride around.',
 2, 6, 'ANYWHERE', 2, 'PHYSICAL', 1),

('דגדוגים', 'Tickle Time',
 'דקה של דגדוגים ומשחק פיזי. תן לו גם לדגדג אותך!',
 'One minute of tickles and physical play. Let them tickle you too!',
 2, 8, 'HOME', 2, 'PHYSICAL', 1),

('כדור בין הרגליים', 'Ball Between Legs',
 'העברת כדור או בלון בין הרגליים - כמה פעמים תצליחו בלי להפיל?',
 'Pass a ball or balloon between legs - how many times without dropping?',
 4, 10, 'HOME', 4, 'PHYSICAL', 2);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE father_goal IS 'Weekly and monthly quality time goals for fathers';
COMMENT ON TABLE flash_mission_template IS 'Library of quick 2-5 minute spontaneous missions';
COMMENT ON COLUMN father.weekly_goal_minutes IS 'Target weekly quality time in minutes (default 30)';
COMMENT ON COLUMN father.total_quality_minutes IS 'Lifetime total quality time tracked';
COMMENT ON COLUMN flash_mission_template.context IS 'Where the mission can be done: HOME, CAR, OUTDOOR, ANYWHERE';
