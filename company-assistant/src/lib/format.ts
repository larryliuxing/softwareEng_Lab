export function pad(n: number): string {
  return String(n).padStart(2, '0')
}

export function ymd(year: number, month: number, day: number): string {
  return `${year}-${pad(month)}-${pad(day)}`
}

export function todayStamp(date = new Date()): string {
  return ymd(date.getFullYear(), date.getMonth() + 1, date.getDate())
}

export function shiftDay(offset: number, from = new Date()): string {
  const date = new Date(from.getFullYear(), from.getMonth(), from.getDate() + offset)
  return ymd(date.getFullYear(), date.getMonth() + 1, date.getDate())
}

export function isPastDeadline(deadline: string, now = new Date()): boolean {
  return todayStamp(now) > deadline
}

export function formatChinese(date: string): string {
  const [year, month, day] = date.split('-')
  return `${year}年${Number(month)}月${Number(day)}日`
}

export function weekdayLabel(date: string): string {
  const [year, month, day] = date.split('-').map(Number)
  return `星期${'日一二三四五六'[new Date(year, month - 1, day).getDay()]}`
}

export function formatDateTime(iso: string): string {
  const date = new Date(iso)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function formatSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

export function greeting(date = new Date()): string {
  const hour = date.getHours()
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

export function uid(prefix: string): string {
  return `${prefix}-${Math.random().toString(36).slice(2, 8)}${Date.now().toString(36).slice(-3)}`
}

export function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate()
}

/** Monday-first cells. Nulls are leading blanks. */
export function monthCells(year: number, month: number): (number | null)[] {
  const first = new Date(year, month - 1, 1).getDay()
  const offset = first === 0 ? 6 : first - 1
  const total = daysInMonth(year, month)
  const cells: (number | null)[] = Array.from({ length: offset }, () => null)
  for (let day = 1; day <= total; day += 1) cells.push(day)
  while (cells.length % 7 !== 0) cells.push(null)
  return cells
}
