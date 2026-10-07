// Scripted demo conversations for the demo widget.
// SOURCE OF TRUTH: marketing/whatsapp-demo-flow.md. Change the doc first, then mirror it here.
// DRAFT: the coach lines come from the workflow rules and are to be replaced line by line with real
// qa-lab transcripts before launch (see the doc). Fully deterministic: no network, no LLM, no randomness.
//
// Step kinds:
//   { kind: 'scene', text }                         caption next to the phone
//   { kind: 'day', text }                           a day pill inside the chat ("יום שלישי")
//   { kind: 'msg', from, time, text, notify? }      from: 'coach' | 'dad'
//        notify: true → proactive; first shown as a lock-screen notification, then in the chat
//   { kind: 'event', time, text }                   something that happens without a message
//        (shown on the locked phone, then as a quiet note in the chat)
//   { kind: 'progress', goal, done, planned, belt, note? }   the "השבוע שלך" card (hours)
//   { kind: 'outro', text }                         the takeaway shown when the scenario ends

export const CHILD = 'נועה';
export const DAD = 'אורי';

export const scenarios = [
  {
    id: 'start',
    label: 'ההודעה הראשונה',
    steps: [
      { kind: 'scene', text: 'יום ראשון, 21:10. אורי ראה מודעה, השאיר שם ונייד, ושלח בוואטסאפ את ההודעה שכבר הייתה כתובה.' },
      { kind: 'progress', goal: 0, done: 0, planned: 0, belt: 'white' },
      { kind: 'msg', from: 'dad', time: '21:10', text: 'היי, אני רוצה להתחיל' },
      { kind: 'msg', from: 'coach', time: '21:10',
        text: 'היי, כיף שבאת 🙂 אני Dad Coach. אתה בוחר כמה זמן אתה רוצה עם הילדים בכל שבוע, ואני עוזר שזה באמת יקרה: נכניס את הזמן לשבוע, אזכיר לפני ואשאל אחרי איך היה. כל מפגש שקרה מקדם אותך בחגורות, מהלבנה ועד השחורה. איך לקרוא לך?' },
      { kind: 'msg', from: 'dad', time: '21:11', text: 'אורי' },
      { kind: 'msg', from: 'coach', time: '21:11', text: 'נעים מאוד, אורי. ספר לי על אחד הילדים: מה השם ובאיזה גיל?' },
      { kind: 'msg', from: 'dad', time: '21:11', text: 'נועה, בת 7' },
      { kind: 'msg', from: 'coach', time: '21:11', text: 'רק מוודא: אתה אורי, ויש לך את נועה בת 7. לשמור?' },
      { kind: 'msg', from: 'dad', time: '21:12', text: 'כן' },
      { kind: 'msg', from: 'coach', time: '21:12', text: 'מעולה! עכשיו בוא נקבע יעד שבועי שיתאים לך ולנועה, ומיד נכניס אותו לשבוע.' },
      { kind: 'msg', from: 'dad', time: '21:12', text: 'יאללה' },
      { kind: 'msg', from: 'coach', time: '21:12',
        text: 'אז נועה בת 7, גיל מדהים! להתחלה אני ממליץ על 2–3 שעות בשבוע: מספיק כדי להרגיש את זה, בלי לחץ. מתי בשבוע יש לך בדרך כלל זמן?' },
      { kind: 'msg', from: 'dad', time: '21:13', text: 'אחר הצהריים. נלך על 3 שעות' },
      { kind: 'msg', from: 'coach', time: '21:13', text: 'קבעתי: 3 שעות השבוע 💪 יש חלון בשלישי ב-17:00. לקבוע שם שעה עם נועה?' },
      { kind: 'progress', goal: 3, done: 0, planned: 0, belt: 'white' },
      { kind: 'outro', text: 'שלוש דקות בוואטסאפ: שם, ילדה אחת, יעד. בלי להוריד אפליקציה ובלי טופס.' },
    ],
  },
  {
    id: 'session',
    label: 'מפגש שקרה',
    steps: [
      { kind: 'scene', text: 'יום ראשון, 21:20. אורי קובע את המפגש הראשון השבוע בהודעה אחת.' },
      { kind: 'progress', goal: 3, done: 0, planned: 0, belt: 'white' },
      { kind: 'msg', from: 'dad', time: '21:20', text: 'נועה, שלישי 17:00, שעה' },
      { kind: 'msg', from: 'coach', time: '21:20', text: 'יופי, קבעתי! אזכיר לך ביום עצמו ושעה לפני. השבוע: שעה מתוכננת מתוך 3.' },
      { kind: 'progress', goal: 3, done: 0, planned: 1, belt: 'white' },
      { kind: 'day', text: 'יום שלישי' },
      { kind: 'scene', text: 'יום שלישי, יום המפגש. Dad Coach כותב שלוש פעמים, קצר: בבוקר, שעה לפני, ואחרי.' },
      { kind: 'msg', from: 'coach', time: '08:00', notify: true, text: 'היום ב-17:00 זה הזמן שלך ושל נועה 🙂' },
      { kind: 'msg', from: 'coach', time: '16:00', notify: true,
        text: 'עוד שעה הזמן שלך ושל נועה. אם עוד אין תוכנית, כמה רעיונות לגיל 7: מבצר מכריות בסלון, אפייה של עוגיות, או סיבוב אופניים בפארק.' },
      { kind: 'msg', from: 'coach', time: '18:30', notify: true, text: 'נו, איך היה לכם עם נועה?' },
      { kind: 'msg', from: 'dad', time: '18:41', text: 'היה מעולה. בנינו מבצר מכריות והיא לא הסכימה לצאת ממנו 😄' },
      { kind: 'msg', from: 'coach', time: '18:41', text: 'איזה כיף! רשמתי ✔ השבוע: שעה מתוך 3 כבר קרתה. זה המפגש הראשון שלך, עוד 2 לחגורה הצהובה.' },
      { kind: 'progress', goal: 3, done: 1, planned: 0, belt: 'white', note: 'עוד 2 מפגשים לחגורה הצהובה' },
      { kind: 'outro', text: 'הודעה אחת קבעה את המפגש. שלוש תזכורות קטנות בזמן הנכון, ותשובה אחת סגרה אותו.' },
    ],
  },
  {
    id: 'cancel',
    label: 'משהו התבטל',
    steps: [
      { kind: 'scene', text: 'יום חמישי, 15:20. נקבעה לאורי שעה עם נועה ב-17:00, ועכשיו ישיבה בעבודה נמשכת.' },
      { kind: 'progress', goal: 3, done: 2, planned: 1, belt: 'white' },
      { kind: 'msg', from: 'dad', time: '15:20', text: 'היום לא אצליח עם נועה, נתקעתי בעבודה' },
      { kind: 'msg', from: 'coach', time: '15:20', text: 'אין בעיה, קורה. ביטלתי את היום. כדי לא לאבד את הזמן השבוע: יש חלון בשישי ב-10:00, לקבוע במקום?' },
      { kind: 'progress', goal: 3, done: 2, planned: 0, belt: 'white' },
      { kind: 'msg', from: 'dad', time: '15:24', text: 'כן, שישי בבוקר מעולה' },
      { kind: 'msg', from: 'coach', time: '15:24', text: 'יופי, קבעתי לשישי ב-10:00! אזכיר לך ביום עצמו ושעה לפני. השבוע שוב מכוסה 💪' },
      { kind: 'progress', goal: 3, done: 2, planned: 1, belt: 'white' },
      { kind: 'outro', text: 'בלי "חבל" ובלי "שוב?". הביטול לא הקטין את השבוע: Dad Coach הציע זמן אחר מיד, ואורי אמר כן. ביטול גם לא שובר את הרצף.' },
    ],
  },
  {
    id: 'sunday',
    label: 'סיכום של יום ראשון',
    steps: [
      { kind: 'scene', text: 'יום ראשון, 09:00. שבוע חדש מתחיל, ועוד אין לו יעד.' },
      { kind: 'progress', goal: 0, done: 0, planned: 0, belt: 'yellow' },
      { kind: 'msg', from: 'coach', time: '09:00', notify: true,
        text: 'בוקר טוב אורי ☀️ בשבוע שעבר עמדת ביעד: 3 שעות מתוך 3 עם נועה, כולל מבצר המכריות. נשאיר 3 שעות גם השבוע, או לשנות?' },
      { kind: 'msg', from: 'dad', time: '09:14', text: 'בוא נעלה ל-4' },
      { kind: 'msg', from: 'coach', time: '09:14', text: 'סגור, 4 שעות השבוע. יש חלונות בשלישי ובחמישי ב-17:00, שעתיים בכל פעם. לקבוע את שניהם עם נועה?' },
      { kind: 'progress', goal: 4, done: 0, planned: 0, belt: 'yellow' },
      { kind: 'msg', from: 'dad', time: '09:15', text: 'כן' },
      { kind: 'msg', from: 'coach', time: '09:15', text: 'יופי, קבעתי! אזכיר לך ביום עצמו ושעה לפני. השבוע מתוכנן כולו: 4 מתוך 4 שעות.' },
      { kind: 'progress', goal: 4, done: 0, planned: 4, belt: 'yellow' },
      { kind: 'outro', text: 'פעם בשבוע, הודעה אחת: איך היה השבוע שעבר, וכמה זמן השבוע. שתי תשובות, והשבוע בלוח.' },
    ],
  },
  {
    id: 'quiet',
    label: 'שבוע מכוסה',
    steps: [
      { kind: 'scene', text: 'יום שני עד רביעי. השבוע של אורי כבר מתוכנן כולו, 4 מתוך 4 שעות.' },
      { kind: 'progress', goal: 4, done: 0, planned: 4, belt: 'yellow' },
      { kind: 'event', day: 'יום שני', time: '09:00', text: 'בדיקת הבוקר: השבוע מכוסה. אין סיבה לכתוב, אז Dad Coach לא כותב.' },
      { kind: 'event', day: 'יום רביעי', time: '09:00', text: 'עדיין מכוסה. גם היום, שום הודעה.' },
      { kind: 'scene', text: 'יום חמישי, 08:00. היום יש מפגש, אז יש סיבה לכתוב.' },
      { kind: 'day', text: 'יום חמישי' },
      { kind: 'msg', from: 'coach', time: '08:00', notify: true, text: 'היום ב-17:00 זה הזמן שלך ושל נועה 🙂' },
      { kind: 'outro', text: 'כשהשבוע מכוסה, Dad Coach שקט. לא "רק בודק מה נשמע", לא "אולי עוד מפגש?". הוא כותב כשיש בשביל מה: לפני מפגש, אחריו, או כשמשהו צריך תיקון.' },
    ],
  },
];
