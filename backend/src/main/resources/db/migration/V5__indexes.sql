-- Child indexes
CREATE INDEX idx_child_father ON child(father_id);

-- Goal indexes
CREATE INDEX idx_goal_father_status ON goal(father_id, status);

-- Habit indexes
CREATE INDEX idx_habit_father_status ON habit(father_id, status);

-- Mission indexes
CREATE INDEX idx_mission_father_status ON mission(father_id, status);
CREATE INDEX idx_mission_child ON mission(child_id);
CREATE INDEX idx_mission_father_assigned ON mission(father_id, assigned_at);

-- Memory indexes
CREATE INDEX idx_memory_father_status ON memory(father_id, status);
CREATE INDEX idx_memory_father_category ON memory(father_id, category);
CREATE INDEX idx_memory_expires ON memory(expires_at) WHERE status = 'ACTIVE';

-- Conversation indexes
CREATE INDEX idx_conversation_father_status ON conversation(father_id, status);
CREATE INDEX idx_conversation_expires ON conversation(expires_at) WHERE status = 'ACTIVE';

-- Coaching session indexes
CREATE INDEX idx_coaching_session_father ON coaching_session(father_id);

-- Notification indexes
CREATE INDEX idx_notification_status_scheduled ON notification(status, scheduled_for);
CREATE INDEX idx_notification_father_day ON notification(father_id, scheduled_for);

-- Reflection indexes
CREATE INDEX idx_reflection_father ON reflection(father_id);
