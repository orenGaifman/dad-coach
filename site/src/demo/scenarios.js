// Scripted demo conversations for the demo widget.
// SOURCE OF TRUTH: marketing/whatsapp-demo-flow.md. Change the doc first, then mirror it here.
// DRAFT: the coach lines come from the workflow rules and are to be replaced line by line with real
// qa-lab transcripts before launch (see the doc). Fully deterministic: no network, no LLM, no randomness.
//
// Step kinds:
//   { kind: 'scene', text }                         caption next to the phone
//   { kind: 'day', text }                           a day pill inside the chat ("יום שלישי")
//   { kind: 'msg', from, time, text, notify?, buttons? }      from: 'coach' | 'dad'
//        notify: true → proactive; first shown as a lock-screen notification, then in the chat
//        buttons: the reply buttons under a coach message (plain text); *bold* in text renders bold
//   { kind: 'event', time, text }                   something that happens without a message
//        (shown on the locked phone, then as a quiet note in the chat)
//   { kind: 'progress', goal, done, planned, belt, note? }   the "השבוע שלך" card (hours)
//   { kind: 'outro', text }                         the takeaway shown when the scenario ends

export const CHILD = 'נועה';
// The product's identity line: every coach message on WhatsApp opens with it (the player adds it to each coach
// bubble and lock-screen notification, so the lines below leave it out, exactly like the doc).
export const COACH_LINE = '❤️ דאד קואץ׳:';
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
        text: 'היי, כיף שהצטרפת 🙂 אני דאד קואץ׳.\nאתה בוחר כמה זמן בשבוע עם הילדים, ואני דואג שזה יקרה:\n• קובע איתך מתי\n• מזכיר לפני\n• שואל אחרי איך היה\n\nאיך קוראים לך?' },
      { kind: 'msg', from: 'dad', time: '21:11', text: 'אורי' },
      { kind: 'msg', from: 'coach', time: '21:11', text: 'נעים מאוד, אורי. ספר לי על אחד הילדים: מה השם ובאיזה גיל?' },
      { kind: 'msg', from: 'dad', time: '21:11', text: 'נועה, בת 7' },
      { kind: 'msg', from: 'coach', time: '21:11', text: 'רק מוודא: אתה אורי, ויש לך את נועה בת 7. לשמור?' },
      { kind: 'msg', from: 'dad', time: '21:12', text: 'כן' },
      { kind: 'msg', from: 'coach', time: '21:12', text: 'מעולה! עכשיו בוא נקבע יעד שבועי שיתאים לך ולנועה, ומיד נכניס אותו לשבוע.' },
      { kind: 'msg', from: 'dad', time: '21:12', text: 'יאללה' },
      { kind: 'msg', from: 'coach', time: '21:12',
        text: 'אז נועה בת 7, גיל מעולה למבצרים.\nלהתחלה אני ממליץ על 2-3 שעות בשבוע, בלי לחץ.\nמתי בשבוע יש לך בדרך כלל זמן?' },
      { kind: 'msg', from: 'dad', time: '21:13', text: 'אחר הצהריים. נלך על 3 שעות' },
      { kind: 'msg', from: 'coach', time: '21:13', text: 'קבעתי יעד: 3 שעות השבוע 💪\nיש חלון *ביום שלישי ב-17:00*.\nלקבוע שם שעה עם נועה?' },
      { kind: 'progress', goal: 3, done: 0, planned: 0, belt: 'white' },
      { kind: 'outro', text: 'אחרי הטופס, עוד שלוש דקות בוואטסאפ: שם, ילדה אחת, יעד. בלי להוריד אפליקציה.' },
    ],
  },
  {
    id: 'session',
    label: 'מפגש שקרה',
    steps: [
      { kind: 'scene', text: 'יום ראשון, 21:20. אורי קובע את המפגש הראשון השבוע בהודעה אחת.' },
      { kind: 'progress', goal: 3, done: 0, planned: 0, belt: 'white' },
      { kind: 'msg', from: 'dad', time: '21:20', text: 'נועה, שלישי 17:00, שעה' },
      { kind: 'msg', from: 'coach', time: '21:20', text: 'קבעתי 🎉 *יום שלישי 13.10 ב-17:00*, שעה עם נועה.\nאזכיר לך בבוקר ושעה לפני, ואשאל אחר כך איך היה.\nהשבוע: שעה מתוכננת מתוך 3.' },
      { kind: 'progress', goal: 3, done: 0, planned: 1, belt: 'white' },
      { kind: 'day', text: 'יום שלישי' },
      { kind: 'scene', text: 'יום שלישי, יום המפגש. דאד קואץ׳ כותב שלוש פעמים, קצר: בבוקר, שעה לפני, ואחרי.' },
      { kind: 'msg', from: 'coach', time: '08:00', notify: true, text: '*היום ב-17:00* זה הזמן שלך ושל נועה 🙂' },
      { kind: 'msg', from: 'coach', time: '16:00', notify: true,
        text: 'עוד שעה הזמן שלך ושל נועה 🙂\nיש כבר רעיון מה תעשו?', buttons: ['רוצה רעיונות'] },
      { kind: 'msg', from: 'dad', time: '16:02', text: 'רוצה רעיונות' },
      { kind: 'msg', from: 'coach', time: '16:02',
        text: '3 רעיונות לשעה עם נועה:\n• פנקייקים יחד, עם נועה במדידות ובערבוב\n• משחק לוח לבחירת נועה\n• סיבוב אופניים בפארק או בשכונה\n\nתספר לי אחר כך איך היה 🙂' },
      { kind: 'msg', from: 'coach', time: '18:30', notify: true, text: 'נו, איך היה לכם עם נועה?', buttons: ['היה מעולה', 'לא יצא'] },
      { kind: 'msg', from: 'dad', time: '18:41', text: 'היה מעולה. בנינו מבצר מכריות והיא לא הסכימה לצאת ממנו 😄' },
      { kind: 'msg', from: 'coach', time: '18:41', text: 'איזה כיף! מבצר שאי אפשר לצאת ממנו.\nרשמתי את השעה עם נועה.\nהשבוע: שעה מתוך 3 💪' },
      { kind: 'progress', goal: 3, done: 1, planned: 0, belt: 'white', note: 'עוד 2 מפגשים לחגורה הצהובה' },
      { kind: 'outro', text: 'הודעה אחת קבעה את המפגש. שתי תזכורות קצרות בזמן הנכון, רעיונות בלחיצה, שאלה אחת אחרי, ותשובה אחת סגרה אותו.' },
    ],
  },
  {
    id: 'cancel',
    label: 'משהו התבטל',
    steps: [
      { kind: 'scene', text: 'יום חמישי, 15:20. נקבעה לאורי שעה עם נועה ב-17:00, ועכשיו ישיבה בעבודה נמשכת.' },
      { kind: 'progress', goal: 3, done: 2, planned: 1, belt: 'white' },
      { kind: 'msg', from: 'dad', time: '15:20', text: 'היום לא אצליח עם נועה, נתקעתי בעבודה' },
      { kind: 'msg', from: 'coach', time: '15:20', text: 'אין בעיה, ביטלתי את היום עם נועה.\nכדי לא לאבד את הזמן השבוע, יש חלון *ביום שישי ב-10:00*.\nלקבוע במקום?' },
      { kind: 'progress', goal: 3, done: 2, planned: 0, belt: 'white' },
      { kind: 'msg', from: 'dad', time: '15:24', text: 'כן, שישי בבוקר מעולה' },
      { kind: 'msg', from: 'coach', time: '15:24', text: 'קבעתי 🎉 *מחר, יום שישי 16.10 ב-10:00*, שעה עם נועה.\nאזכיר לך בבוקר ושעה לפני, ואשאל אחר כך איך היה.\nהשבוע מכוסה.' },
      { kind: 'progress', goal: 3, done: 2, planned: 1, belt: 'white' },
      { kind: 'outro', text: 'בלי ״חבל״ ובלי ״שוב?״. הביטול לא הקטין את השבוע: דאד קואץ׳ הציע זמן אחר מיד, ואורי אמר כן. וביטול לא מוריד חגורה.' },
    ],
  },
  {
    id: 'sunday',
    label: 'סיכום של יום ראשון',
    steps: [
      { kind: 'scene', text: 'יום ראשון, 09:00. שבוע חדש מתחיל, ועוד אין לו יעד.' },
      { kind: 'progress', goal: 0, done: 0, planned: 0, belt: 'yellow' },
      { kind: 'msg', from: 'coach', time: '09:00', notify: true,
        text: 'בוקר טוב אורי 🙂\nבשבוע שעבר: 3 שעות מתוך 3 עם נועה, כולל מבצר המכריות.\nנשאיר 3 שעות גם השבוע, או לשנות?' },
      { kind: 'msg', from: 'dad', time: '09:14', text: 'בוא נעלה ל-4' },
      { kind: 'msg', from: 'coach', time: '09:14', text: 'סגור, 4 שעות השבוע 💪\n• *יום שלישי ב-17:00*, שעתיים\n• *יום חמישי ב-17:00*, שעתיים\nלקבוע את שניהם עם נועה?' },
      { kind: 'progress', goal: 4, done: 0, planned: 0, belt: 'yellow' },
      { kind: 'msg', from: 'dad', time: '09:15', text: 'כן' },
      { kind: 'msg', from: 'coach', time: '09:15', text: 'קבעתי 🎉 שעתיים עם נועה בכל אחד:\n• *יום שלישי 20.10 ב-17:00*\n• *יום חמישי 22.10 ב-17:00*\nאזכיר לך בבוקר ושעה לפני, ואשאל אחר כך איך היה.\nהשבוע מכוסה.' },
      { kind: 'progress', goal: 4, done: 0, planned: 4, belt: 'yellow' },
      { kind: 'outro', text: 'פעם בשבוע, הודעה אחת: איך היה השבוע שעבר, וכמה זמן השבוע. שתי תשובות, והשבוע מתוכנן.' },
    ],
  },
  {
    id: 'quiet',
    label: 'שבוע מכוסה',
    steps: [
      { kind: 'scene', text: 'יום שני עד רביעי. השבוע של אורי כבר מתוכנן כולו, 4 מתוך 4 שעות.' },
      { kind: 'progress', goal: 4, done: 0, planned: 4, belt: 'yellow' },
      { kind: 'event', day: 'יום שני', time: '09:00', text: 'בדיקת הבוקר: השבוע מכוסה. אין סיבה לכתוב, אז דאד קואץ׳ לא כותב.' },
      { kind: 'event', day: 'יום רביעי', time: '09:00', text: 'עדיין מכוסה. גם היום, שום הודעה.' },
      { kind: 'scene', text: 'יום חמישי, 08:00. היום יש מפגש, אז יש סיבה לכתוב.' },
      { kind: 'day', text: 'יום חמישי' },
      { kind: 'msg', from: 'coach', time: '08:00', notify: true, text: '*היום ב-17:00* זה הזמן שלך ושל נועה 🙂' },
      { kind: 'outro', text: 'כשהשבוע מכוסה, דאד קואץ׳ שקט. לא "רק בודק מה נשמע", לא "אולי עוד מפגש?". הוא כותב כשיש בשביל מה: לפני מפגש, אחריו, או כשמשהו צריך תיקון.' },
    ],
  },
];
