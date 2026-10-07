// Every WhatsApp line in the films, copied verbatim from the qa-lab transcripts (real workflow dad-coach-3, real model):
// qa-lab/transcripts/v4-s1-first-week.md (יואב, נועה 7), v4-s2-recovery.md (a father with איתי). A bubble may be shortened
// only by dropping whole sentences - marked "cut:" with what was dropped and why. The platform's prefix line
// "❤️ Dad Coach:" is part of every coach message, as WhatsApp shows it.
const P = '❤️ Dad Coach:\n';

export const M = {
  // ---- s1: the first week (יואב, נועה 7)
  hi: 'היי, שמעתי עליכם, אשמח להתחיל',
  welcome: P + 'היי, איזה כיף שהצטרפת! 😊\n\nאני Dad Coach - אני עוזר לאבות להפוך כוונות טובות לזמן איכות שבאמת קורה עם הילדים. ביחד נקבע יעד שבועי שמתאים לך, נתכנן אותו בתוך השבוע, אני אזכיר לך לפני כל מפגש ואבדוק מה קרה אחרי - ובדרך גם צוברים "חגורות" על מפגשים שהושלמו (כולם מתחילים בחגורה לבנה).\n\nבוא נתחיל - איך קוראים לך?',
  name: 'יואב',
  askChild: P + 'נעים מאוד, יואב! 😊\nועכשיו ספר לי על הילד/ה - מה השם והגיל?',
  child: 'נועה, בת 7',
  confirmQ: P + 'אז יש לי: יואב, ונועה בת 7 - נכון? 😊',
  yes: 'כן, נכון',
  registered: P + 'מעולה, יואב! נרשמת בהצלחה עם נועה. 🎉\nעכשיו בוא נקבע יעד שבועי שיתאים לך ולנועה - וכבר נכניס אותו לשבוע הקרוב.',
  goal: '3 שעות בשבוע נשמע טוב',
  goalSet: P + 'קבעתי יעד של 3 שעות לשבוע הזה 💪\n\nיש לך פתוח היום ב-17:00, מחר (חמישי) ב-17:00, או בסופ"ש בבוקר/אחה"צ. מתי נוח לך לקבוע מפגש ראשון עם נועה?',
  book: 'בוא נקבע היום ב-16:55 עם נועה, שעה',
  booked: P + 'קבעתי! היום ב-16:55 שעה עם נועה 🎉 אזכיר לך שעה לפני.\n\nהשבוע מכוסה עכשיו ב-60 מתוך 180 דקות - נשארו עוד 120 דקות ליעד. רוצה שנקבע עוד משהו כבר עכשיו, או נדבר על זה אחרי המפגש?',
  // cut: the second paragraph (the week's coverage) - the ad shows the booking itself
  bookedShort: P + 'קבעתי! היום ב-16:55 שעה עם נועה 🎉 אזכיר לך שעה לפני.',
  reminder1h: P + 'עוד שעה הזמן שלך עם נועה 😊\nיש לך כבר רעיון מה תעשו? אפשר למשל בישול קטן יחד (פנקייקים?), משחק לוח, או טיול אופניים אם בא לכם החוצה.',
  followUp: P + 'נו, איך היה לכם עם נועה? 😊',
  happened: 'היה מדהים! בנינו מגדל לגו ענק והיא לא הפסיקה לצחוק',
  recorded: P + 'איזה כיף! מגדל לגו ענק וצחוקים - בדיוק מזה עשוי זמן איכות 🧱😄 נרשם, וזה גם המפגש הראשון שלכם - רצף של שבוע אחד כבר מתחיל!\n\nנשארו 120 דקות ליעד השבוע. רוצה שנקבע עוד מפגש אחד או שניים כדי לסגור את זה?',
  howAmI: 'איך אני עומד השבוע?',
  standing: P + 'אתה ב-60 מתוך 180 דקות השבוע (שליש מהיעד) - נשארו עוד 120 דקות כדי לכסות את השבוע 💪\n\nרוצה שנמצא עוד חלון או שניים להשלמה?',

  // ---- s2: when something is cancelled (a father with איתי; his goal there is two hours)
  bookItai: 'תקבע לי מחר ב-17:00 עם איתי, שעה',
  bookedItai: P + 'קבעתי! מחר ב-17:00, שעה עם איתי 🎉 אזכיר לך בבוקר ושעה לפני.\n\nגם קבעתי את יעד השבוע על שעתיים - עם המפגש הזה כבר כיסית 60 מתוך 120 דקות, נשארו עוד 60 💪',
  // cut: the second paragraph (that father's two-hour goal) where the video is יואב's week of three hours
  bookedItaiShort: P + 'קבעתי! מחר ב-17:00, שעה עם איתי 🎉 אזכיר לך בבוקר ושעה לפני.',
  cancel: 'אוף, מחר לא אוכל, תבטל את זה',
  cancelled: P + 'בוטל, אין בעיה. כדי לא לאבד את השעתיים השבוע - יש חלון היום ב-18:00-19:00, או ביום שישי בבוקר. מה מתאים יותר?',
  rebook: 'בעצם כן, תקבע במקום ביום שאחרי באותה שעה',
  rebooked: P + 'קבעתי! יום שישי ב-17:00, שעה עם איתי 🎉 אזכיר לך בבוקר ושעה לפני.\n\nעם זה כיסית 60 מתוך 120 דקות השבוע, נשארו עוד 60 - נסגור את זה בהמשך, אין לחץ 👍',
  missed: 'לא הספקנו בסוף, היה יום מטורף',
  missedReply: P + 'קורה, העיקר שממשיכים 🙏 רשמתי שלא יצא.\n\nכדי לא לפספס את השעתיים השבוע - יש חלון היום ב-17:00-18:00, או מחר (חמישי) באותה שעה. מתאים אחד מהם?',
  // cut: the second paragraph. In the lab the clock stayed on Wednesday while the Friday session was moved into the
  // past, so after a Friday session it offers "מחר (חמישי)" - right in the lab, wrong-looking on screen.
  missedReplyShort: P + 'קורה, העיקר שממשיכים 🙏 רשמתי שלא יצא.',
};
