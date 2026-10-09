import type { DayStatus } from '../types'

function hash(text: string): number {
  let value = 2166136261
  for (let index = 0; index < text.length; index += 1) {
    value ^= text.charCodeAt(index)
    value = Math.imul(value, 16777619)
  }
  return value >>> 0
}

export function baseAttendance(userId: string, date: string): { status: DayStatus; source: string } {
  const [year, month, day] = date.split('-').map(Number)
  const weekday = new Date(year, month - 1, day).getDay()
  if (weekday === 0 || weekday === 6) {
    return { status: 'rest', source: '出勤补充表' }
  }
  const bucket = hash(`${userId}|${date}`) % 19
  if (bucket === 0 || bucket === 1) return { status: 'leave', source: '内网导出' }
  if (bucket === 2) return { status: 'trip', source: '内网导出' }
  if (bucket === 3) return { status: 'local', source: '内网导出' }
  return { status: 'work', source: '出勤补充表' }
}
