import { describe, expect, it } from 'vitest'
import { explainVoiceNoteError } from './voiceNotes'

describe('explainVoiceNoteError', () => {
  it('names an ElevenLabs plan out of credit', () => {
    expect(explainVoiceNoteError('ELEVENLABS_QUOTA_EXCEEDED')).toContain('נגמרו הקרדיטים')
  })
  it('names a key id set instead of the key', () => {
    expect(explainVoiceNoteError('ELEVENLABS_API_KEY_ID_USED_AS_API_KEY')).toContain('מזהה המפתח')
  })
  it('names a refused key, a slow service and a Meta download', () => {
    expect(explainVoiceNoteError('ELEVENLABS_INVALID_API_KEY')).toContain('לא קיבל את המפתח')
    expect(explainVoiceNoteError('ELEVENLABS_HTTP_401')).toContain('לא קיבל את המפתח')
    expect(explainVoiceNoteError('ELEVENLABS_TIMEOUT')).toContain('לא ענה בזמן')
    expect(explainVoiceNoteError('ELEVENLABS_HTTP_503')).toContain('לא ענה בזמן')
    expect(explainVoiceNoteError('META_MEDIA_HTTP_404')).toContain('מוואטסאפ')
  })
  it('keeps an unknown code as it came', () => {
    expect(explainVoiceNoteError('UNEXPECTED')).toBe('UNEXPECTED')
  })
})
