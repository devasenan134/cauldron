import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useMe } from '../auth'
import { addDays, today } from '../dates'
import { dayChipLabel, num } from '../format'

export function Pill({ children, tone = 'sand', className = '' }: { children: ReactNode; tone?: 'sand' | 'paper' | 'ember' | 'bright' | 'amber' | 'sky' | 'ink'; className?: string }) {
  const cls = {
    sand: 'bg-sand text-ink',
    paper: 'bg-paper text-ink',
    ember: 'bg-ember-soft text-ember',
    bright: 'bg-ember-bright text-on-go',
    amber: 'bg-pink-deep text-white',
    sky: 'bg-sky-soft text-sky-deep',
    ink: 'bg-ink text-cream',
  }[tone]
  return <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ${cls} ${className}`}>{children}</span>
}

export function Button({
  children, onClick, variant = 'primary', type = 'button', disabled, className = '',
}: {
  children: ReactNode
  onClick?: () => void
  variant?: 'primary' | 'accent' | 'ghost' | 'soft'
  type?: 'button' | 'submit'
  disabled?: boolean
  className?: string
}) {
  const cls = {
    primary: 'bg-ink text-cream hover:opacity-85',
    accent: 'bg-ember-bright text-on-go hover:opacity-90',
    ghost: 'border border-stone-300 text-ink hover:bg-paper',
    soft: 'bg-ember-soft text-ember hover:opacity-85',
  }[variant]
  return (
    <button type={type} onClick={onClick} disabled={disabled}
      className={`press inline-flex items-center justify-center gap-2 rounded-full px-5 py-2.5 text-sm font-bold transition-colors disabled:opacity-40 ${cls} ${className}`}>
      {children}
    </button>
  )
}

export function Chip({ children, selected, onClick, disabled }: { children: ReactNode; selected: boolean; onClick: () => void; disabled?: boolean }) {
  return (
    <button onClick={onClick} disabled={disabled} aria-pressed={selected}
      className={`press shrink-0 whitespace-nowrap rounded-full px-4 py-2 text-sm font-semibold transition-colors disabled:pointer-events-none disabled:opacity-35 ${selected ? 'bg-ink text-cream' : 'bg-paper text-ink ring-1 ring-stone-200 hover:bg-sand'}`}>
      {children}
    </button>
  )
}

/** Your initial in a circle; opens your profile. */
export function Avatar({ size = 40 }: { size?: number }) {
  const me = useMe().data
  const initial = (me?.name || me?.email || '?').charAt(0).toUpperCase()
  return (
    <Link to="/profile" title="Profile" aria-label="Profile"
      className="press grid shrink-0 place-items-center rounded-full bg-paper font-display font-bold text-ink shadow-[0_4px_16px_rgba(0,0,0,0.10)] ring-1 ring-stone-200"
      style={{ width: size, height: size, fontSize: size * 0.42 }}>
      {initial}
    </Link>
  )
}

/** The big page title, like the app's screen headers. */
export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end gap-3">
      {/* On a phone the actions drop below the title rather than squeeze over it. */}
      <div className="min-w-0 flex-1 basis-48">
        {subtitle && <p className="text-sm text-stone-500">{subtitle}</p>}
        <h1 className="font-display text-4xl font-extrabold sm:text-5xl">{title}</h1>
      </div>
      {actions}
    </div>
  )
}

export function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-3 mt-8 flex items-center">
      <h2 className="flex-1 font-display text-2xl font-bold">{children}</h2>
      {action}
    </div>
  )
}

export function Empty({ emoji, title, body, action }: { emoji: string; title: string; body: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-6 py-12 text-center">
      <div className="text-5xl">{emoji}</div>
      <h3 className="mt-3 font-display text-xl font-bold">{title}</h3>
      <p className="mt-1 max-w-sm text-stone-500">{body}</p>
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export const Shimmer = ({ className }: { className: string }) => <div className={`shimmer ${className}`} />

/** "−  3  +" */
export function Stepper({
  value, onChange, step = 1, min = step, label, tone = 'ink', big,
}: { value: number; onChange: (v: number) => void; step?: number; min?: number; label?: string; tone?: 'ink' | 'amber'; big?: boolean }) {
  const color = tone === 'amber' ? 'text-amber-deep' : 'text-ink'
  const bg = tone === 'amber' ? 'bg-amber-soft hover:opacity-80' : 'bg-sand hover:opacity-80'
  const size = big ? 'h-10 w-10 text-xl' : 'h-7 w-7 text-base'
  const canLess = value - step >= min - 1e-9
  return (
    <div className={`flex items-center gap-1 ${color}`}>
      {label && <span className="mr-1 text-xs font-semibold">{label}</span>}
      <button aria-label={`Decrease ${label ?? 'amount'}`} disabled={!canLess} onClick={() => onChange(value - step)}
        className={`press grid place-items-center rounded-full font-bold disabled:opacity-30 ${bg} ${size}`}>−</button>
      <span key={value} className={`rise text-center font-display font-bold tabular-nums ${big ? 'w-12 text-3xl' : 'w-8 text-base'}`}>{num(value)}</span>
      <button aria-label={`Increase ${label ?? 'amount'}`} onClick={() => onChange(value + step)}
        className={`press grid place-items-center rounded-full font-bold ${bg} ${size}`}>+</button>
    </div>
  )
}

/** Pick a day from the next [days] days, or the queue (null). */
export function DayChips({ selected, onPick, from = today(), days = 10 }: { selected: string | null; onPick: (d: string | null) => void; from?: string; days?: number }) {
  const options: (string | null)[] = [null, ...Array.from({ length: days }, (_, i) => addDays(from, i))]
  if (selected !== null && !options.includes(selected)) options.splice(1, 0, selected)
  return (
    <div className="flex flex-wrap gap-2">
      {options.map((d) => <Chip key={d ?? 'queue'} selected={d === selected} onClick={() => onPick(d)}>{dayChipLabel(d)}</Chip>)}
    </div>
  )
}

export const inputCls =
  'rounded-full border-0 bg-paper px-5 py-3 text-base shadow-sm ring-1 ring-stone-200 outline-none placeholder:text-stone-400 focus:ring-2 focus:ring-ember-bright/50'
