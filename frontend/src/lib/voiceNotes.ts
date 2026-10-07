/**
 * D-029 (Big Boss D-176): why the last voice note was not heard, as the owner can act on it. The backend sends a
 * safe code only (never the key, the audio or the words); a code this table does not know is shown as it came.
 */
export function explainVoiceNoteError(code: string): string {
  if (code === 'ELEVENLABS_QUOTA_EXCEEDED') {
    return 'נגמרו הקרדיטים במנוי ElevenLabs. הם מתחדשים בתחילת תקופת החיוב, או אפשר לשדרג את המנוי. עד אז האבות מתבקשים לכתוב במילים.'
  }
  if (code === 'ELEVENLABS_API_KEY_ID_USED_AS_API_KEY') {
    return 'בשרת הוגדר מזהה המפתח של ElevenLabs במקום המפתח עצמו. המפתח מתחיל ב-sk_ ומוצג רק כשיוצרים אותו - צריך ליצור מפתח חדש ולשים אותו ב-ELEVENLABS_API_KEY.'
  }
  if (/^ELEVENLABS_(.*API_KEY.*|HTTP_401|HTTP_403)$/.test(code)) {
    return 'ElevenLabs לא קיבל את המפתח - צריך לבדוק את ELEVENLABS_API_KEY בהגדרות השרת.'
  }
  if (/^ELEVENLABS_(TIMEOUT|UNREACHABLE)$/.test(code) || /^ELEVENLABS_HTTP_5/.test(code)) {
    return 'ElevenLabs לא ענה בזמן. אם זה חוזר, כדאי לבדוק את הסטטוס שלהם.'
  }
  if (code.startsWith('META_MEDIA')) {
    return 'לא הצלחנו להוריד את ההקלטה מוואטסאפ.'
  }
  return code
}
