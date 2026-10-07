import { describe, expect, it } from 'vitest'
import { ageLabel, dayLabel, daysLeft, displayPhone, duration, percent, weekRange } from './format'
import { beltImage, beltName, toNextBelt } from './belts'
import { bookingPrompt, coachLink, DASHBOARD_WORD } from './whatsapp'
import { homeFor, safeNext } from './session'
import type { Me } from './types'

describe('duration', () => {
  it('speaks Hebrew hours and minutes', () => {
    expect(duration(60)).toBe('שעה')
    expect(duration(120)).toBe('שעתיים')
    expect(duration(180)).toBe('3 שעות')
    expect(duration(45)).toBe('45 דק׳')
    expect(duration(105)).toBe('שעה ו-45 דק׳')
    expect(duration(0)).toBe('0 דק׳')
  })
})

describe('days', () => {
  it('is relative to the father\'s own today', () => {
    expect(dayLabel('2026-10-07', 'WEDNESDAY', '2026-10-07')).toBe('היום')
    expect(dayLabel('2026-10-08', 'THURSDAY', '2026-10-07')).toBe('מחר')
    expect(dayLabel('2026-10-06', 'TUESDAY', '2026-10-07')).toBe('אתמול')
    expect(dayLabel('2026-10-10', 'SATURDAY', '2026-10-07')).toBe('יום שבת, 10.10')
    expect(dayLabel('2026-11-01', 'SUNDAY', '2026-10-31')).toBe('מחר')
  })
  it('counts what is left of the week', () => {
    expect(daysLeft(1)).toBe('השבוע נגמר היום')
    expect(daysLeft(2)).toBe('נשארו יומיים')
    expect(daysLeft(5)).toBe('נשארו 5 ימים')
    expect(weekRange('2026-10-04', '2026-10-10')).toBe('4.10–10.10')
  })
})

describe('labels', () => {
  it('belts', () => {
    expect(beltName('YELLOW')).toBe('חגורה צהובה')
    expect(beltImage('BLACK')).toBe('/belts/black-belt.webp')
    expect(toNextBelt('ORANGE', 7)).toBe('עוד 7 מפגשים לחגורה כתומה')
    expect(toNextBelt('ORANGE', 1)).toBe('עוד מפגש אחד לחגורה כתומה')
    expect(toNextBelt(null, null)).toContain('השחורה')
  })
  it('ages and phones', () => {
    expect(ageLabel(7)).toBe('גיל 7')
    expect(ageLabel(2)).toBe('גיל שנתיים')
    expect(displayPhone('+972501234567')).toBe('050-123-4567')
    expect(displayPhone('+19995550001')).toBe('+19995550001')
    expect(percent(60, 180)).toBe(33)
    expect(percent(200, 180)).toBe(100)
    expect(percent(5, null)).toBe(0)
  })
})

describe('links', () => {
  it('wa.me with a prefilled message, never without a number', () => {
    expect(coachLink('+19995550100', 'היי')).toBe('https://wa.me/19995550100?text=%D7%94%D7%99%D7%99')
    expect(coachLink(null, 'x')).toBeNull()
    expect(bookingPrompt('נועה')).toBe('רוצה לקבוע זמן עם נועה ביום שלישי')
  })
  it('next paths stay inside the dashboard', () => {
    expect(safeNext('/sessions')).toBe('/sessions')
    expect(safeNext('/admin/fathers/3')).toBe('/admin/fathers/3')
    expect(safeNext('//evil.example')).toBeNull()
    expect(safeNext('https://evil.example')).toBeNull()
    expect(safeNext('/login')).toBeNull()
  })
})

describe('homeFor (D-028)', () => {
  const me = (capabilities: string[]): Me => ({ kind: capabilities.includes('admin') ? 'ADMIN' : 'FATHER', name: 'אורן', capabilities, fatherId: 1 })
  it('lands a father on his own page, also when he is on the team', () => {
    expect(homeFor(me(['father']))).toBe('/home')
    expect(homeFor(me(['father', 'training', 'admin']))).toBe('/home')
  })
  it('lands a team member who is not a father on the admin', () => {
    expect(homeFor(me(['admin']))).toBe('/admin')
    expect(homeFor(me([]))).toBe('/login')
  })
})

describe('the dashboard word (D-027)', () => {
  it('is what he writes to the coach for the button', () => {
    expect(coachLink('+972501234567', DASHBOARD_WORD)).toBe('https://wa.me/972501234567?text=%D7%93%D7%A9%D7%91%D7%95%D7%A8%D7%93')
  })
})
