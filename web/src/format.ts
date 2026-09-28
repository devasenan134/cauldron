import { addDays, dayLabel, today } from './dates'

/** Sanity CDN images are huge; ask for a resized one. */
export function thumb(url: string | null, w: number, h = w): string | undefined {
  if (!url) return undefined
  return url.includes('cdn.sanity.io') ? `${url}?w=${w}&h=${h}&fit=crop&auto=format` : url
}

export const kcal = (n: number | null | undefined) => (n == null ? '—' : `${Math.round(n).toLocaleString()} kcal`)

/** 1 -> "1", 1.5 -> "1.5" */
export const num = (n: number) => (Number.isInteger(n) ? String(n) : String(Math.round(n * 10) / 10))

export const plural = (n: number, one: string, many = one + 's') => `${num(n)} ${n === 1 ? one : many}`

export function dayChipLabel(day: string | null): string {
  if (day === null) return 'Queue'
  if (day === today()) return 'Today'
  if (day === addDays(today(), 1)) return 'Tomorrow'
  const { weekday, date } = dayLabel(day)
  return `${weekday} ${date}`
}
