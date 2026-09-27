import type { ReactNode } from 'react'

/** Sanity CDN images are huge; ask for a resized one. */
export function thumb(url: string | null, w: number, h = w): string | undefined {
  if (!url) return undefined
  return url.includes('cdn.sanity.io') ? `${url}?w=${w}&h=${h}&fit=crop&auto=format` : url
}

export const kcal = (n: number | null | undefined) => (n == null ? '—' : `${Math.round(n)} kcal`)

export function Pill({ children, tone = 'stone' }: { children: ReactNode; tone?: 'stone' | 'ember' }) {
  const cls = tone === 'ember' ? 'bg-ember-soft text-ember' : 'bg-stone-200/70 text-stone-700'
  return <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}>{children}</span>
}

export function Button({
  children,
  onClick,
  variant = 'primary',
  type = 'button',
  disabled,
}: {
  children: ReactNode
  onClick?: () => void
  variant?: 'primary' | 'ghost'
  type?: 'button' | 'submit'
  disabled?: boolean
}) {
  const cls =
    variant === 'primary'
      ? 'bg-ink text-cream hover:bg-stone-700'
      : 'border border-stone-300 text-stone-700 hover:bg-stone-100'
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`rounded-lg px-3 py-1.5 text-sm font-medium transition disabled:opacity-50 ${cls}`}
    >
      {children}
    </button>
  )
}

export const inputCls =
  'rounded-lg border border-stone-300 bg-white px-3 py-1.5 text-sm outline-none focus:border-ember focus:ring-2 focus:ring-ember/20'
