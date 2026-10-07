import { useEffect, type ReactNode } from 'react'
import { Link } from 'react-router'
import { Logomark } from '../../shared/Icon'
import styles from './Legal.module.css'

// Ported from dad-coach-web (app/privacy, app/terms, app/data-deletion; last updated 1.8.2026) into Hebrew and
// brought in line with the product as it is (WhatsApp coach, quality-time sessions, optional Google Calendar,
// the AI Workflow Platform). A DRAFT for legal review - not reviewed by a lawyer yet.
const CONTACT = 'oren26g@gmail.com'

function LegalPage({ title, children }: { title: string; children: ReactNode }) {
  useEffect(() => { document.title = `${title} · דאד קואץ׳` }, [title])
  return (
    <main className={styles.screen}>
      <article className={styles.doc}>
        <Link to="/" className={styles.brand}><Logomark size={32} /> דאד קואץ׳</Link>
        <p className={styles.draft} role="note">טיוטה לבדיקת עו״ד</p>
        <h1>{title}</h1>
        <p className={styles.updated}>עודכן לאחרונה: 7 באוקטובר 2026</p>
        {children}
        <nav className={styles.nav} aria-label="מסמכים">
          <Link to="/privacy">מדיניות פרטיות</Link>
          <Link to="/terms">תנאי שימוש</Link>
          <Link to="/data-deletion">מחיקת נתונים</Link>
        </nav>
      </article>
    </main>
  )
}

export function Privacy() {
  return (
    <LegalPage title="מדיניות פרטיות">
      <section><h2>1. מי אנחנו</h2>
        <p>דאד קואץ׳ ("אנחנו") הוא מאמן בוואטסאפ שעוזר לאבות לתכנן זמן איכות עם הילדים ולעמוד בו. המדיניות הזאת מסבירה איזה מידע אנחנו אוספים, למה, ואיך אנחנו שומרים עליו.</p></section>
      <section><h2>2. איזה מידע אנחנו אוספים</h2>
        <ul>
          <li>מספר הטלפון שלך (דרך וואטסאפ), והשם שבחרת.</li>
          <li>ההודעות שאתה שולח למאמן, והתשובות שלו.</li>
          <li>פרטי הילדים שמסרת: שם וגיל.</li>
          <li>היעדים השבועיים, המפגשים שקבעת, מה שאישרת שקרה, והערות שכתבת.</li>
          <li>אם חיברת יומן Google - גישה ליומן כדי לבדוק זמנים פנויים ולהוסיף את המפגשים.</li>
          <li>אזור הזמן ושפת הממשק.</li>
        </ul></section>
      <section><h2>3. למה אנחנו משתמשים בו</h2>
        <ul>
          <li>כדי לאמן אותך: לתכנן את השבוע, להזכיר לפני מפגש ולשאול איך היה.</li>
          <li>כדי להציג לך את הלוח שלך (השבוע, המפגשים, ההתקדמות).</li>
          <li>כדי לשלוח לך הודעות בוואטסאפ, כולל כפתור הכניסה לדף שלך.</li>
          <li>כדי לשפר את המאמן ואת השירות.</li>
        </ul></section>
      <section><h2>4. מי מעבד את המידע</h2>
        <p>השיחות מעובדות בפלטפורמת AI שמפעילה את המאמן, וההודעות עוברות דרך WhatsApp (Meta). אם חיברת יומן, המפגשים נכתבים ליומן Google שלך. איננו מוכרים מידע אישי.</p></section>
      <section><h2>5. אבטחה</h2>
        <p>המידע מוצפן בתעבורה ובאחסון. הכניסה לדף שלך היא בכפתור אישי שהמאמן שולח לך בוואטסאפ, בלי סיסמה. "יציאה מכל המכשירים" מנתקת כל מכשיר ומבטלת גם את כפתורי הכניסה הקודמים.</p></section>
      <section><h2>6. כמה זמן אנחנו שומרים</h2>
        <p>כל עוד אתה משתמש בשירות, כדי שהמאמן יכיר את ההיסטוריה שלך. אפשר לבקש מחיקה בכל עת (ראה "מחיקת נתונים").</p></section>
      <section><h2>7. הזכויות שלך</h2>
        <p>אפשר לעיין במידע, לתקן אותו או למחוק אותו. לכל בקשה בנושא פרטיות: <a href={`mailto:${CONTACT}`} className="ltr">{CONTACT}</a>.</p></section>
    </LegalPage>
  )
}

export function Terms() {
  return (
    <LegalPage title="תנאי שימוש">
      <section><h2>1. הסכמה לתנאים</h2>
        <p>השימוש בדאד קואץ׳ מהווה הסכמה לתנאים האלה. אם אינך מסכים, אל תשתמש בשירות.</p></section>
      <section><h2>2. מה השירות</h2>
        <p>דאד קואץ׳ הוא מאמן מבוסס בינה מלאכותית בוואטסאפ: עוזר לקבוע יעד שבועי של זמן עם הילדים, לקבוע מפגשים, מזכיר ושואל איך היה, ומציג את ההתקדמות בלוח אישי.</p></section>
      <section><h2>3. האחריות שלך</h2>
        <ul>
          <li>השירות מיועד לבני 18 ומעלה.</li>
          <li>שמור על הטלפון ועל כפתור הכניסה שלך; הוא אישי, אל תעביר אותו הלאה.</li>
          <li>אין להשתמש בשירות לרעה או למטרה שאינה חוקית.</li>
        </ul></section>
      <section><h2>4. הבהרה</h2>
        <p>דאד קואץ׳ נותן הכוונה כללית להורות, ואינו תחליף לייעוץ רפואי, פסיכולוגי או משפטי. בכל שאלה על בריאות הילד או התפתחותו - פנה לאיש מקצוע.</p></section>
      <section><h2>5. הגבלת אחריות</h2>
        <p>השירות ניתן כמות שהוא (AS IS), בלי התחייבות מכל סוג. איננו אחראים לנזק שנגרם מהשימוש בו.</p></section>
      <section><h2>6. שינויים</h2>
        <p>אנחנו עשויים לעדכן את התנאים מעת לעת. המשך השימוש אחרי עדכון מהווה הסכמה לתנאים המעודכנים.</p></section>
      <section><h2>7. יצירת קשר</h2>
        <p>לשאלות על התנאים: <a href={`mailto:${CONTACT}`} className="ltr">{CONTACT}</a>.</p></section>
    </LegalPage>
  )
}

export function DataDeletion() {
  return (
    <LegalPage title="מחיקת נתונים">
      <section><h2>איך מבקשים מחיקה</h2>
        <p>זכותך לבקש למחוק את המידע האישי שלך מדאד קואץ׳. יש שלוש דרכים:</p></section>
      <section><h2>1. מהלוח</h2>
        <p>בלוח שלך: הגדרות ← "מחיקת הנתונים שלי", ומקלידים את מילת האישור. החשבון נסגר מיד.</p></section>
      <section><h2>2. בוואטסאפ</h2>
        <p>שלח למאמן את ההודעה <strong className="ltr">DELETE MY DATA</strong>.</p></section>
      <section><h2>3. במייל</h2>
        <ul>
          <li>לכתובת <a href={`mailto:${CONTACT}`} className="ltr">{CONTACT}</a>, בנושא "בקשת מחיקת נתונים".</li>
          <li>ציין את מספר הוואטסאפ שלך ואשר שאתה רוצה למחוק את כל הנתונים.</li>
        </ul></section>
      <section><h2>מה נמחק</h2>
        <ul>
          <li>מספר הטלפון והשם שלך.</li>
          <li>פרטי הילדים, היעדים, המפגשים וההערות.</li>
          <li>כל היסטוריית השיחות עם המאמן.</li>
          <li>ההעדפות וההגדרות, וכל המידע הנלווה.</li>
        </ul></section>
      <section><h2>כמה זמן זה לוקח</h2>
        <p>מהלוח ומוואטסאפ - החשבון נסגר מיד והמחיקה מסתיימת בדרך כלל תוך דקות. בקשות במייל מטופלות תוך 30 יום לכל היותר. תקבל אישור כשהמחיקה הושלמה.</p></section>
    </LegalPage>
  )
}
