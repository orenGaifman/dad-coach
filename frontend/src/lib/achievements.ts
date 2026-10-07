/** Hebrew title + line per achievement key (backend ProgressService). */
export const ACHIEVEMENTS: Record<string, { title: string; text: string }> = {
  'first-session': { title: 'המפגש הראשון', text: 'אישרת את המפגש הראשון שלך.' },
  'goal-met': { title: 'שבוע שנסגר', text: 'עמדת ביעד של שבוע שלם.' },
  'every-child': { title: 'זמן לכל אחד', text: 'היה לך מפגש עם כל אחד מהילדים.' },
  'shared-a-moment': { title: 'רגע ששיתפת', text: 'כתבת מה עשיתם באחד המפגשים.' },
  'two-weeks': { title: 'שבועיים ברצף', text: 'עמדת ביעד שבועיים ברצף.' },
  'ten-sessions': { title: '10 מפגשים', text: 'עשרה מפגשים שקרו באמת.' },
  'four-weeks': { title: 'חודש ברצף', text: 'ארבעה שבועות ברצף ביעד.' },
}

export function achievement(key: string) {
  return ACHIEVEMENTS[key] ?? { title: key, text: '' }
}
