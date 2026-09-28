// Dates travel as local "YYYY-MM-DD" strings; never through UTC.

export function iso(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function parse(s: string): Date {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function addDays(s: string, n: number): string {
  const d = parse(s)
  d.setDate(d.getDate() + n)
  return iso(d)
}

/** Monday of the week containing s. */
export function weekStart(s: string): string {
  const d = parse(s)
  return addDays(s, -((d.getDay() + 6) % 7))
}

export const today = () => iso(new Date())

export function dayLabel(s: string): { weekday: string; date: string } {
  const d = parse(s)
  return {
    weekday: d.toLocaleDateString(undefined, { weekday: 'short' }),
    date: d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
  }
}

/** Whole days from s to today (negative for future days). */
export function daysAgo(s: string): number {
  return Math.round((parse(today()).getTime() - parse(s).getTime()) / 86_400_000)
}
