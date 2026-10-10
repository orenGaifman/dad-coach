// The dashboard API's shapes (backend com.dadcoach.web.*).

export interface Me {
  kind: 'FATHER' | 'ADMIN'
  name: string
  capabilities: string[]
  fatherId: number | null
}

export type Phase = 'UPCOMING' | 'IN_PROGRESS' | 'AWAITING_CONFIRMATION' | 'COMPLETED' | 'CANCELLED' | 'MISSED'
export type Belt = 'WHITE' | 'YELLOW' | 'ORANGE' | 'GREEN' | 'BLUE' | 'BROWN' | 'BLACK'

export interface Session {
  id: string
  childId: number
  childName: string | null
  phase: Phase
  start: string
  end: string
  localDate: string
  weekday: string
  localStart: string
  localEnd: string
  durationMinutes: number
  notes: string | null
  canConfirm: boolean
  canCancel: boolean
}

export interface ChildName { id: number; name: string }

export interface Progress {
  belt: Belt
  nextBelt: Belt | null
  totalCompleted: number
  beltStartsAt: number
  nextBeltAt: number | null
  sessionsToNextBelt: number | null
  percentToNextBelt: number
  streakWeeks: number
  sessionStreak: number
}

export interface Home {
  name: string
  timezone: string
  today: string
  week: { start: string; end: string; daysLeft: number }
  goal: { exists: boolean; targetHours: number | null; targetMinutes: number | null; creditedMinutes: number | null; status: string | null }
  coverage: { targetMinutes: number | null; completedMinutes: number; plannedMinutes: number; uncoveredMinutes: number | null; covered: boolean | null; awaitingMinutes: number }
  nextSession: Session | null
  sessionsThisWeek: Session[]
  awaitingConfirmation: Session[]
  progress: Progress
  children: ChildName[]
  calendarConnected: boolean
  coachWhatsApp: string | null
}

export interface SessionsView {
  upcoming: Session[]
  awaiting: Session[]
  past: Session[]
  children: ChildName[]
  timezone: string
}

export interface Child { id: number; name: string; age: number; birthYear: number | null }

export interface ProgressView {
  progress: Progress
  belts: { belt: Belt; from: number; to: number | null; reached: boolean; current: boolean }[]
  achievements: { key: string; image: string; earned: boolean }[]
  weeks: { weekStart: string; targetHours: number; creditedMinutes: number; status: string; met: boolean }[]
}

export interface Settings {
  name: string
  timezone: string
  calendarConnected: boolean
  calendarReconnectRequired: boolean
  calendarAvailable: boolean
  deleteConfirmation: string
}

export interface TrainingLibrary {
  available: boolean
  videos: { slug: string; primary: boolean; title: string; description: string; durationSeconds: number;
            videoUrl: string | null; posterUrl: string | null; started: boolean; completed: boolean }[]
}

// admin

export interface Overview {
  weekStart: string
  fathersByStatus: Record<string, number>
  activeThisWeek: number
  goalsSet: number
  goalsMet: number
  sessionsThisWeek: number
  sessionsCompletedThisWeek: number
  failedDeliveries7d: number
  pendingDeletions: number
}

export interface FatherRow {
  id: number
  name: string | null
  phone: string
  status: string
  belt: Belt
  completed: number
  calendarConnected: boolean
  createdAt: string
  lastActivity: string | null
  children: number
  deletionPending: boolean
}

export interface DeliveryRow {
  kind: 'SCHEDULED' | 'LOGIN_LINK'
  fatherId: number | null
  fatherName: string | null
  what: string | null
  status: string
  reason: string | null
  at: string
}

export interface FatherDetail {
  profile: { id: number; name: string | null; phone: string; status: string; timezone: string; createdAt: string;
             lastInteractionAt: string | null; belt: Belt; completed: number; streakWeeks: number; longestStreakWeeks: number;
             calendarConnected: boolean; onboardingState: string | null; workflowState: string | null }
  children: { id: number; name: string; age: number; status: string }[]
  goals: { weekStart: string; targetHours: number; creditedMinutes: number; status: string }[]
  sessions: { id: string; childName: string | null; phase: Phase; start: string; end: string; notes: string | null }[]
  deliveries: DeliveryRow[]
  loginLinks: { createdAt: string; expiresAt: string; usedAt: string | null; lastUsedAt: string | null; useCount: number;
                revokedAt: string | null; deliveryStatus: string; deliveryError: string | null;
                receiptStatus: string | null }[]
  liveDashboardSessions: number
  deletion: { requestedAt: string; attempts: number; nextAttemptAt: string; lastError: string | null; completedAt: string | null; outcome: string | null; purgeLocal: boolean } | null
  deactivatedAt: string | null
  deleteConfirmation: string
}

export interface Integrations {
  whatsapp: { configured: boolean; publicNumber: string | null; lastInbound: string | null; lastOutbound: string | null; lastFailure: { reason: string; at: string } | null }
  platform: { enabled: boolean; baseUrlSet: boolean; reachable: boolean; adminPanelConfigured: boolean; callbackEnabled: boolean; callbackTemplate: boolean }
  calendarOAuthConfigured: boolean
  opsApiConfigured: boolean
  trainingMediaConfigured: boolean
  webBaseUrl: string
  /** D-029: absent from a backend without voice notes. */
  voiceNotes?: VoiceNotesStatus
}

/** D-029: the admin's on/off for WhatsApp voice notes, whether an ElevenLabs key is set (never the key), the last note. */
export interface VoiceNotesStatus {
  enabled: boolean
  configured: boolean
  lastHeardAt: string | null
  lastFailedAt: string | null
  lastError: string | null
}

/** The templates screen: a catalog template as submitted to Meta, its live state there, and whether Dad Coach sends it now. */
export interface AdminTemplateRow {
  name: string
  language: string
  category: string
  purpose: string
  body: string
  maxVariables: number
  examples: string[]
  quickReplies: string[]
  sample: string
  general: boolean
  /** APPROVED, PENDING, REJECTED, PAUSED, DISABLED; MISSING (not at Meta); UNKNOWN (Meta not read yet) */
  metaStatus: string
  metaCategory: string | null
  /** set when Meta re-filed it, e.g. UTILITY → MARKETING */
  metaPreviousCategory: string | null
  metaRejectedReason: string | null
  metaBodyMatches: boolean
  registeredStatus: string | null
  ready: boolean
  inUseAs: string | null
}

export interface AdminTemplates {
  wabaId: string | null
  metaConfigured: boolean
  refreshedAt: string | null
  lastError: string | null
  templates: AdminTemplateRow[]
}

export interface AdminTraining {
  mediaConfigured: boolean
  videos: { slug: string; primary: boolean; order: number; title: string; description: string; durationSeconds: number; active: boolean; hasFile: boolean }[]
}
