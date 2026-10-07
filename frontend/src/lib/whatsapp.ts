/** A wa.me link to the coach with a prefilled message (no PII in it - only what he will send himself). */
export function coachLink(number: string | null | undefined, text: string): string | null {
  if (!number) return null
  const digits = number.replace(/\D/g, '')
  if (digits.length < 8) return null
  return `https://wa.me/${digits}?text=${encodeURIComponent(text)}`
}

/** What to write to the coach to book time with a child (the empty states quote it). */
export function bookingPrompt(childName?: string | null): string {
  return childName ? `רוצה לקבוע זמן עם ${childName} ביום שלישי` : 'רוצה לקבוע זמן עם הילדים השבוע'
}
