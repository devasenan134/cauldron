import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, type Meal, type Plan, type PlanEntry } from '../api'
import { today } from '../dates'
import { refreshPlan } from '../plan'

export const MEAL_LABEL: Record<Meal, string> = { breakfast: 'Breakfast', lunch: 'Lunch', dinner: 'Dinner' }
export const MEAL_EMOJI: Record<Meal, string> = { breakfast: '🍳', lunch: '🥪', dinner: '🍲' }

/** The meal it's time for (or next): what a new plan entry for today defaults to. */
export function mealNow(): Meal {
  const h = new Date().getHours()
  return h < 11 ? 'breakfast' : h < 16 ? 'lunch' : 'dinner'
}

/** Pick breakfast, lunch or dinner. */
export function MealPicker({ meal, onPick }: { meal: Meal; onPick: (m: Meal) => void }) {
  return (
    <div className="inline-flex rounded-full bg-sand p-1">
      {(['breakfast', 'lunch', 'dinner'] as const).map((m) => (
        <button key={m} onClick={() => onPick(m)}
          className={`press rounded-full px-4 py-1.5 text-sm ${m === meal ? 'bg-paper font-bold shadow-sm' : 'font-medium text-stone-500'}`}>{MEAL_LABEL[m]}</button>
      ))}
    </div>
  )
}

/** Meals you can log: planned for today or earlier, and not making a prep. */
export const loggable = (e: PlanEntry) => !e.is_prep && e.day != null && e.day <= today()

/** Ask for a rough kcal count for eating out; undefined if cancelled, null if skipped. */
export function askOutKcal(current?: number | null): number | null | undefined {
  const v = prompt('Ate out: roughly how many kcal? (leave blank if you don’t know)', current != null ? String(Math.round(current)) : '')
  if (v == null) return undefined
  const n = Number(v.replace(/[^\d.]/g, ''))
  return v.trim() && n > 0 ? n : null
}

/** Log a planned meal as eaten, eaten out (it goes to the fridge), or undo the log. */
export function useLogMeal(entry: PlanEntry) {
  const qc = useQueryClient()
  const m = useMutation({
    mutationFn: (log: { status: 'eaten' | 'out' | null; out_kcal?: number | null }) => api.updateEntry(entry.id, log),
    onMutate: ({ status, out_kcal }) => qc.setQueriesData<Plan>({ queryKey: ['plan'] }, (p) => p && {
      ...p,
      days: Object.fromEntries(Object.entries(p.days).map(([d, l]) => [d, l.map((e) => e.id !== entry.id ? e : {
        ...e, status, out_kcal: status === 'out' ? out_kcal ?? null : null,
        eaten_kcal: status === 'out' ? out_kcal ?? null : status === 'eaten' ? e.kcal : null,
      })])),
    }),
    onSettled: () => refreshPlan(qc),
  })
  return {
    eaten: () => m.mutate({ status: 'eaten' }),
    out: () => { const k = askOutKcal(); if (k !== undefined) m.mutate({ status: 'out', out_kcal: k }) },
    undo: () => m.mutate({ status: null }),
    editOut: () => { const k = askOutKcal(entry.out_kcal); if (k !== undefined) m.mutate({ status: 'out', out_kcal: k }) },
  }
}

/** Log eating out at a meal with nothing planned. */
export function useLogOut(day: string, meal: Meal) {
  const qc = useQueryClient()
  const m = useMutation({
    mutationFn: (out_kcal: number | null) => api.addEntry({ day, meal, status: 'out', out_kcal }),
    onSettled: () => refreshPlan(qc),
  })
  return () => { const k = askOutKcal(); if (k !== undefined) m.mutate(k) }
}

/** ✓ Ate it / Ate out buttons, or what was logged (tap to undo). */
export function LogButtons({ entry, size = 'sm' }: { entry: PlanEntry; size?: 'sm' | 'md' }) {
  const log = useLogMeal(entry)
  const pad = size === 'md' ? 'px-3.5 py-1.5 text-sm' : 'px-2.5 py-1 text-xs'
  if (entry.status === 'eaten') {
    return (
      <button onClick={log.undo} title="Logged as eaten: tap to undo"
        className={`press rounded-full bg-ember-bright font-bold text-on-go ${pad}`}>✓ Eaten</button>
    )
  }
  if (entry.status === 'out') {
    return (
      <span className="inline-flex items-center gap-1">
        <button onClick={log.editOut} title="Change the kcal"
          className={`press rounded-full bg-danger font-bold text-white ${pad}`}>
          Ate out{entry.out_kcal != null ? ` · ${Math.round(entry.out_kcal)}` : ''}
        </button>
        <button onClick={log.undo} title="Undo" className={`press rounded-full text-stone-500 hover:bg-sand ${pad}`}>Undo</button>
      </span>
    )
  }
  const isMeal = entry.recipe_id != null || entry.leftover_of != null
  return (
    <span className="inline-flex items-center gap-1">
      {isMeal && (
        <button onClick={log.eaten} title="Log it: counts toward today's calories"
          className={`press rounded-full bg-ink font-bold text-cream ${pad}`}>✓ Ate it</button>
      )}
      <button onClick={log.out} title={isMeal ? 'You ate out: this meal goes to the fridge' : 'You ate out'}
        className={`press rounded-full font-bold text-danger ring-1 ring-danger/40 hover:bg-danger/10 ${pad}`}>Ate out</button>
    </span>
  )
}
