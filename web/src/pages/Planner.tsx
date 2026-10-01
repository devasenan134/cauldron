import {
  DndContext,
  DragOverlay,
  MouseSensor,
  TouchSensor,
  pointerWithin,
  rectIntersection,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type CollisionDetection,
  type DragStartEvent,
} from '@dnd-kit/core'
import { SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useContext, useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, MEALS, type Meal, type Plan, type PlanEntry, type RecipeSummary } from '../api'
import { LogButtons, loggable, MEAL_EMOJI, MEAL_LABEL } from '../components/MealLog'
import { addDays, dayLabel, today, weekStart } from '../dates'
import { refreshPlan } from '../plan'
import { Button, Chip, PageHeader, Shimmer, Stepper } from '../components/ui'
import { kcal, num, plural, thumb } from '../format'
import { useDebounced } from '../useDebounced'

const QUEUE = 'queue'
// A day's panels are lists of their own: "2026-09-29|lunch".
const slot = (day: string, meal: Meal) => `${day}|${meal}`
const parseSlot = (col: string): { day: string | null; meal?: Meal } =>
  col === QUEUE ? { day: null } : { day: col.split('|')[0], meal: col.split('|')[1] as Meal }
const ofMeal = (entries: PlanEntry[], meal: Meal) => entries.filter((e) => e.meal === meal)
// Drop where the pointer is; fall back to overlap when it's between columns.
const collision: CollisionDetection = (args) => {
  const hits = pointerWithin(args)
  return hits.length ? hits : rectIntersection(args)
}

type Drag =
  | { kind: 'recipe'; recipe: RecipeSummary }
  | { kind: 'batch'; batch: PlanEntry }
  | { kind: 'entry'; entry: PlanEntry }

// Tapping a recipe, a batch or a planned meal opens "put it on a day" (what you'd drag, without dragging:
// on a phone a drag needs a long press).
const Place = createContext<(d: Drag) => void>(() => {})

// Controls inside a draggable card: pressing them mustn't start a drag (with the mouse or a finger).
const stop = {
  onPointerDown: (e: React.PointerEvent) => e.stopPropagation(),
  onMouseDown: (e: React.MouseEvent) => e.stopPropagation(),
  onTouchStart: (e: React.TouchEvent) => e.stopPropagation(),
}
// On a draggable: a long press starts a drag, not text selection or the browser's link/image menu.
const noCallout = 'select-none [-webkit-touch-callout:none]'
const phoneWidth = () => typeof window !== 'undefined' && window.matchMedia('(max-width: 639px)').matches


export default function Planner() {
  const [params, setParams] = useSearchParams()
  const start = params.get('week') ?? weekStart(today())
  const setWeek = (w: string) => setParams({ week: w }, { replace: true })
  const qc = useQueryClient()
  const navigate = useNavigate()
  const planKey = ['plan', start]
  const plan = useQuery({ queryKey: planKey, queryFn: () => api.plan(start) })
  const [dragging, setDragging] = useState<Drag | null>(null)
  const [includeQueue, setIncludeQueue] = useState(false)
  // Week: all seven days side by side. Day: one day, big (like the app's two views). Phones start on Day.
  const [view, setView] = useState<'week' | 'day'>(() => (phoneWidth() ? 'day' : 'week'))
  const [picked, setPicked] = useState<string | null>(null)
  const [placing, setPlacing] = useState<Drag | null>(null)

  // Mouse: drag once it moves a few pixels. Touch: press and hold, so a swipe still scrolls the page.
  // (One PointerSensor for both took touches too, and lost them to the browser's scrolling.)
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 8 } }),
  )

  const refresh = () => refreshPlan(qc)
  const add = useMutation({ mutationFn: api.addEntry, onSettled: refresh })
  const move = useMutation({
    mutationFn: ({ id, day, position, meal }: { id: number; day: string | null; position: number; meal?: Meal }) =>
      api.updateEntry(id, { day, position, meal }),
    onSettled: refresh,
  })
  const grocery = useMutation({
    mutationFn: () => api.generateGrocery(start, addDays(start, 6), includeQueue),
    onSuccess: (items) => {
      qc.setQueryData(['grocery'], items)
      navigate('/grocery')
    },
  })

  const columns = (p: Plan): Record<string, PlanEntry[]> => ({
    ...Object.fromEntries(Object.entries(p.days).flatMap(([d, l]) => MEALS.map((m) => [slot(d, m), ofMeal(l, m)]))),
    [QUEUE]: p.queue,
  })

  /** Which column and index a drop target means. */
  const locate = (overId: string, p: Plan): { col: string; index: number } | null => {
    const cols = columns(p)
    if (overId.startsWith('col:')) {
      const col = overId.slice(4)
      return { col, index: cols[col]?.length ?? 0 }
    }
    if (overId.startsWith('e:')) {
      const id = Number(overId.slice(2))
      for (const [col, list] of Object.entries(cols)) {
        const index = list.findIndex((e) => e.id === id)
        if (index >= 0) return { col, index }
      }
    }
    return null
  }

  const onDragStart = (e: DragStartEvent) => {
    setDragging((e.active.data.current as Drag) ?? null)
    navigator.vibrate?.(10)
  }

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    setDragging(null)
    const p = plan.data
    if (!over || !p) return
    const target = locate(String(over.id), p)
    if (target) place(active.data.current as Drag, target, p)
  }

  /** Put what was dragged (or tapped) at a place in the plan: add a recipe or leftovers, or move a meal. */
  const place = (drag: Drag, target: { col: string; index: number }, p: Plan) => {
    const { day, meal } = parseSlot(target.col)

    if (drag.kind === 'recipe') {
      add.mutate({ day, meal, recipe_id: drag.recipe.id, servings: 1, position: target.index })
      return
    }
    if (drag.kind === 'batch') {
      add.mutate({ day, meal, leftover_of: drag.batch.id, servings: 1, position: target.index })
      return
    }
    // Move the entry locally first so the drop feels instant, then save.
    const entry = drag.entry
    const cols = columns(p)
    const from = entry.day ? slot(entry.day, entry.meal) : QUEUE
    const next: Record<string, PlanEntry[]> = Object.fromEntries(Object.entries(cols).map(([k, v]) => [k, [...v]]))
    if (next[from]) next[from] = next[from].filter((e) => e.id !== entry.id)
    if (!next[target.col]) return
    next[target.col].splice(target.index, 0, { ...entry, day, meal: meal ?? entry.meal })
    qc.setQueryData<Plan>(planKey, {
      days: Object.fromEntries(Object.keys(p.days).map((d) => [d, MEALS.flatMap((m) => next[slot(d, m)])])),
      queue: next[QUEUE],
    })
    move.mutate({ id: entry.id, day, position: target.index, meal })
  }

  const days = plan.data ? Object.keys(plan.data.days) : []
  const weekTotal = days.reduce((sum, d) => sum + dayTotal(plan.data!.days[d]), 0)

  return (
    <Place.Provider value={setPlacing}>
    <DndContext sensors={sensors} collisionDetection={collision} onDragStart={onDragStart} onDragEnd={onDragEnd}
      onDragCancel={() => setDragging(null)} autoScroll={{ threshold: { x: 0.15, y: 0.15 }, acceleration: 12 }}>
      <div className="rise">
        <PageHeader title="Plan" subtitle={`Week of ${dayLabel(start).date} · ${kcal(weekTotal)}`} />
        <p className="-mt-3 mb-4 text-sm text-stone-500">
          <span className="pointer-coarse:hidden">Drag a recipe onto a meal, or click it to pick a day.</span>
          <span className="hidden pointer-coarse:inline">Tap a recipe to add it to a day, or press and hold to drag it.</span>
        </p>
        <div className="-mt-2 mb-5 flex flex-wrap items-center gap-2">
          <div className="flex items-center rounded-full bg-paper p-1">
            <button aria-label="Previous week" className="press rounded-full px-3 py-1.5 font-bold hover:bg-sand" onClick={() => setWeek(addDays(start, -7))}>←</button>
            <span className="px-2 text-sm font-semibold">{dayLabel(start).date} – {dayLabel(addDays(start, 6)).date}</span>
            <button aria-label="Next week" className="press rounded-full px-3 py-1.5 font-bold hover:bg-sand" onClick={() => setWeek(addDays(start, 7))}>→</button>
          </div>
          {start !== weekStart(today()) && <Chip selected={false} onClick={() => setWeek(weekStart(today()))}>This week</Chip>}
          <div className="flex rounded-full bg-sand p-1">
            {(['day', 'week'] as const).map((v) => (
              <button key={v} onClick={() => setView(v)}
                className={`press rounded-full px-4 py-1.5 text-sm capitalize ${view === v ? 'bg-paper font-bold shadow-sm' : 'font-medium text-stone-500'}`}>{v}</button>
            ))}
          </div>
          <div className="flex w-full items-center justify-between gap-3 sm:ml-auto sm:w-auto">
            <label className="flex items-center gap-2 text-sm text-stone-600">
              <input type="checkbox" className="h-4 w-4 accent-ember-bright" checked={includeQueue} onChange={(e) => setIncludeQueue(e.target.checked)} />
              include queue
            </label>
            <Button variant="accent" onClick={() => grocery.mutate()} disabled={grocery.isPending}>🛒 Make grocery list</Button>
          </div>
        </div>

        <div className="grid grid-cols-[minmax(0,1fr)] gap-5 lg:grid-cols-[250px_minmax(0,1fr)]">
          <Sidebar />
          <div className="space-y-4">
            {plan.isError && <p className="text-red-700">Couldn't load the plan: {String(plan.error)}</p>}
            {plan.isPending && <div className="grid auto-cols-[minmax(210px,1fr)] grid-flow-col gap-3 overflow-hidden">{Array.from({ length: 7 }, (_, i) => <Shimmer key={i} className="h-64 rounded-3xl" />)}</div>}
            <Column id={QUEUE} title="Queue" subtitle="planned, no day yet" entries={plan.data?.queue ?? []} horizontal />
            {view === 'week' ? (
              /* Days keep a readable width; when the week doesn't fit it scrolls sideways. */
              <div className="-mx-1 grid auto-cols-[minmax(210px,1fr)] grid-flow-col gap-3 overflow-x-auto px-1 pb-3">
                {days.map((d) => {
                  const { weekday, date } = dayLabel(d)
                  return (
                    <Column key={d} id={d} title={weekday} subtitle={date.replace(/^\D+/, '')} entries={plan.data!.days[d]}
                      highlight={d === today()} total={dayTotal(plan.data!.days[d])} />
                  )
                })}
              </div>
            ) : plan.data && (() => {
              const day = picked && days.includes(picked) ? picked : days.includes(today()) ? today() : days[0]
              const { weekday, date } = dayLabel(day)
              return (
                <div>
                  <div className="mb-3 grid grid-cols-7 gap-1 sm:gap-2">
                    {days.map((d) => (
                      <button key={d} onClick={() => setPicked(d)}
                        className={`press rounded-2xl py-2 text-center transition-colors ${d === day ? 'bg-ink text-cream' : 'bg-paper ring-1 ring-stone-200'} ${d === today() && d !== day ? 'ring-2 ring-ember-bright' : ''}`}>
                        <span className="block text-xs opacity-70">{dayLabel(d).weekday}</span>
                        <span className="block font-display text-lg font-bold">{d.slice(8).replace(/^0/, '')}</span>
                        <span className={`mx-auto mt-0.5 block h-1.5 w-1.5 rounded-full ${plan.data!.days[d].length ? 'bg-ember-bright' : ''}`} />
                      </button>
                    ))}
                  </div>
                  <Column id={day} title={day === today() ? 'Today' : weekday} subtitle={date} entries={plan.data.days[day]}
                    highlight={day === today()} total={dayTotal(plan.data.days[day])} wide />
                </div>
              )
            })()}
          </div>
        </div>
      </div>

      <DragOverlay>
        {dragging && (
          <div className="w-56 rotate-2 rounded-2xl bg-paper p-3 text-sm font-semibold shadow-2xl ring-2 ring-ember-bright">
            {dragTitle(dragging)}
          </div>
        )}
      </DragOverlay>
    </DndContext>
    {placing && plan.data && (
      <PlaceDialog drag={placing} days={days} onClose={() => setPlacing(null)}
        onPick={(col) => {
          const p = plan.data!
          place(placing, { col, index: columns(p)[col]?.length ?? 0 }, p)
          setPlacing(null)
        }} />
    )}
    </Place.Provider>
  )
}

const dragTitle = (d: Drag) => d.kind === 'recipe' ? d.recipe.title : d.kind === 'batch' ? `Leftovers: ${d.batch.title}` : d.entry.title

/** Where to put a recipe, leftovers or a planned meal, without dragging: a day (this week) and a meal,
 *  or the queue. A sheet from the bottom on a phone. */
function PlaceDialog({ drag, days, onPick, onClose }: { drag: Drag; days: string[]; onPick: (col: string) => void; onClose: () => void }) {
  const from = drag.kind === 'entry' ? drag.entry.day : null
  const [day, setDay] = useState(from && days.includes(from) ? from : days.includes(today()) ? today() : days[0])
  useEffect(() => {
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', esc)
    return () => window.removeEventListener('keydown', esc)
  }, [onClose])
  const moving = drag.kind === 'entry'
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 sm:items-center sm:p-4" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-label={moving ? 'Move to' : 'Add to'} onClick={(e) => e.stopPropagation()}
        className="rise w-full max-w-md rounded-t-3xl bg-cream p-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] shadow-2xl sm:rounded-3xl">
        <div className="flex items-start gap-3">
          <div className="min-w-0 flex-1">
            <p className="text-xs font-semibold uppercase tracking-wider text-stone-500">{moving ? 'Move' : 'Add to the plan'}</p>
            <h2 className="line-clamp-2 font-display text-2xl font-bold leading-tight">{dragTitle(drag)}</h2>
          </div>
          <button className="p-1 text-xl text-stone-500 hover:text-ink" aria-label="Close" onClick={onClose}>✕</button>
        </div>
        <div className="mt-4 grid grid-cols-7 gap-1">
          {days.map((d) => (
            <button key={d} onClick={() => setDay(d)} aria-pressed={d === day}
              className={`press rounded-2xl py-2 text-center ${d === day ? 'bg-ink text-cream' : 'bg-paper ring-1 ring-stone-200'} ${d === today() && d !== day ? 'ring-2 ring-ember-bright' : ''}`}>
              <span className="block text-[11px] opacity-70">{dayLabel(d).weekday}</span>
              <span className="block font-display text-lg font-bold">{d.slice(8).replace(/^0/, '')}</span>
            </button>
          ))}
        </div>
        <div className="mt-3 grid grid-cols-3 gap-2">
          {MEALS.map((m) => (
            <Button key={m} variant="ghost" className="py-3" onClick={() => onPick(slot(day, m))}>{MEAL_EMOJI[m]} {MEAL_LABEL[m]}</Button>
          ))}
        </div>
        <button className="mt-3 w-full rounded-full px-4 py-2.5 text-sm font-semibold text-stone-500 hover:bg-sand hover:text-ink" onClick={() => onPick(QUEUE)}>
          No day yet: put it in the queue
        </button>
      </div>
    </div>
  )
}

// Planned calories; a meal you ate out instead counts what you logged for it.
const dayTotal = (entries: PlanEntry[]) => entries.reduce((s, e) => s + ((e.status === 'out' ? e.out_kcal : e.kcal) ?? 0), 0)

function Column({ id, title, subtitle, entries, total, highlight, horizontal, wide }: {
  id: string
  title: string
  subtitle: string
  entries: PlanEntry[]
  total?: number
  highlight?: boolean
  horizontal?: boolean
  wide?: boolean
}) {
  const queue = id === QUEUE
  const { setNodeRef, isOver } = useDroppable({ id: `col:${id}`, disabled: !queue })
  return (
    <div ref={queue ? setNodeRef : undefined}
      className={`rounded-3xl p-2.5 transition ${isOver ? 'bg-ember-soft ring-2 ring-ember-bright' : 'bg-paper'} ${highlight && !isOver ? 'ring-2 ring-ember-bright' : ''}`}>
      <div className="mb-2 flex items-baseline justify-between gap-1 px-1.5">
        <span className="whitespace-nowrap">
          <span className={`font-display text-lg font-bold ${highlight ? 'text-ember-bright' : ''}`}>{title}</span>
          <span className="ml-1.5 text-sm text-stone-500">{subtitle}</span>
        </span>
        {total ? <span className="whitespace-nowrap font-display text-sm font-bold tabular-nums text-ember">{Math.round(total)}</span> : null}
      </div>
      {queue ? (
        <SortableContext items={entries.map((e) => `e:${e.id}`)} strategy={verticalListSortingStrategy}>
          <div className={horizontal ? 'flex min-h-14 flex-wrap gap-2' : 'min-h-28 space-y-2'}>
            {entries.map((e) => <EntryCard key={e.id} entry={e} compact={horizontal} />)}
            {entries.length === 0 && <div className="grid min-h-14 place-items-center rounded-2xl border-2 border-dashed border-stone-200 px-2 text-center text-xs text-stone-400">Drop recipes here</div>}
            <AddNote day={null} />
          </div>
        </SortableContext>
      ) : (
        <div className={wide ? 'space-y-3' : 'space-y-2'}>
          {MEALS.map((m) => <MealPanel key={m} day={id} meal={m} entries={ofMeal(entries, m)} wide={wide} />)}
        </div>
      )}
    </div>
  )
}

/** Breakfast, lunch or dinner on one day: a drop target of its own. */
function MealPanel({ day, meal, entries, wide }: { day: string; meal: Meal; entries: PlanEntry[]; wide?: boolean }) {
  const { setNodeRef, isOver } = useDroppable({ id: `col:${slot(day, meal)}` })
  const total = dayTotal(entries)
  return (
    <div ref={setNodeRef} className={`rounded-2xl p-1.5 transition ${isOver ? 'bg-ember-soft ring-2 ring-ember-bright' : 'bg-cream/60'}`}>
      <div className="mb-1 flex items-center justify-between px-1 text-[11px] font-bold uppercase tracking-wider text-stone-500">
        <span>{MEAL_EMOJI[meal]} {MEAL_LABEL[meal]}</span>
        {total ? <span className="tabular-nums">{Math.round(total)}</span> : null}
      </div>
      <SortableContext items={entries.map((e) => `e:${e.id}`)} strategy={verticalListSortingStrategy}>
        <div className={wide ? 'grid min-h-12 gap-2 sm:grid-cols-2 xl:grid-cols-3' : 'min-h-12 space-y-2'}>
          {entries.map((e) => <EntryCard key={e.id} entry={e} />)}
          {entries.length === 0 && <div className="grid min-h-10 place-items-center rounded-xl border border-dashed border-stone-200 text-[11px] text-stone-400">Drop here</div>}
        </div>
      </SortableContext>
      <AddNote day={day} meal={meal} />
    </div>
  )
}

function EntryCard({ entry, compact }: { entry: PlanEntry; compact?: boolean }) {
  const qc = useQueryClient()
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: `e:${entry.id}`,
    data: { kind: 'entry', entry } satisfies Drag,
  })
  // Show a change in every cached week at once; the server's answer replaces it after.
  const optimistic = (change: (e: PlanEntry) => PlanEntry) =>
    qc.setQueriesData<Plan>({ queryKey: ['plan'] }, (p) => p && {
      days: Object.fromEntries(Object.entries(p.days).map(([d, l]) => [d, l.map((e) => (e.id === entry.id ? change(e) : e))])),
      queue: p.queue.map((e) => (e.id === entry.id ? change(e) : e)),
    })
  const update = useMutation({
    mutationFn: (patch: Parameters<typeof api.updateEntry>[1]) => api.updateEntry(entry.id, patch),
    onSettled: () => refreshPlan(qc),
  })
  const remove = useMutation({
    mutationFn: () => api.deleteEntry(entry.id),
    onMutate: () => qc.setQueriesData<Plan>({ queryKey: ['plan'] }, (p) => p && {
      days: Object.fromEntries(Object.entries(p.days).map(([d, l]) => [d, l.filter((e) => e.id !== entry.id && e.leftover_of !== entry.id)])),
      queue: p.queue.filter((e) => e.id !== entry.id),
    }),
    onSettled: () => refreshPlan(qc),
  })
  const setServings = (s: number) => {
    const delta = s - entry.servings
    optimistic((e) => ({ ...e, servings: s, kcal: e.kcal_per_serving != null ? e.kcal_per_serving * s : e.kcal, portions_left: e.portions_left != null ? e.portions_left - delta : null }))
    update.mutate({ servings: s })
  }
  const setCook = (c: number | null) => {
    optimistic((e) => ({ ...e, cook_portions: c, portions_left: c == null ? null : (e.portions_left ?? c - e.servings) + (c - (e.cook_portions ?? c)) }))
    update.mutate({ cook_portions: c })
  }
  const out = entry.status === 'out'
  const isBatch = entry.cook_portions != null && !out
  const pick = useContext(Place)
  const logRow = loggable(entry) && (
    <div className="mt-2 flex flex-wrap items-center gap-1" {...stop}>
      <LogButtons entry={entry} />
    </div>
  )
  // ✕ and "move" show on hover with a mouse, always on a touch screen (no hover there).
  const corner = (
    <div className="absolute -right-1.5 -top-1.5 hidden gap-1 group-hover:flex pointer-coarse:flex" {...stop}>
      <button onClick={() => pick({ kind: 'entry', entry })} title="Move to another day or meal" aria-label="Move"
        className="press grid h-7 w-7 place-items-center rounded-full bg-paper text-xs text-ink shadow ring-1 ring-stone-200">⇄</button>
      <button onClick={() => remove.mutate()} title={entry.cook_portions != null && entry.status !== 'out' ? 'Remove (and its leftovers)' : 'Remove'} aria-label="Remove"
        className="press grid h-7 w-7 place-items-center rounded-full bg-ink text-xs text-cream">✕</button>
    </div>
  )
  const shortfall = entry.short.length > 0 && (
    <p className="mt-1.5 rounded-xl bg-pink-soft px-2 py-1 text-[11px] font-semibold text-pink-deep" {...stop}
      title="Nothing planned makes enough of it; the grocery list buys its ingredients instead">
      Needs {entry.short.map((x) => `${Math.round(x.grams)} g ${x.title.toLowerCase()}`).join(', ')}: buying the ingredients
    </p>
  )

  // Making a prepped ingredient: by weight, and it all goes to the fridge.
  if (entry.is_prep) {
    const setMade = () => {
      const v = prompt(`How many grams of ${entry.title} are you making?`, String(Math.round(entry.made_grams ?? 0)))
      if (v == null || !(Number(v) > 0)) return
      const g = Number(v)
      optimistic((e) => ({ ...e, made_grams: g, grams_left: (e.grams_left ?? 0) + g - (e.made_grams ?? 0) }))
      update.mutate({ made_grams: g })
    }
    return (
      <div ref={setNodeRef} style={{ transform: CSS.Transform.toString(transform), transition }} {...attributes} {...listeners}
        className={`group relative ${noCallout} cursor-grab touch-manipulation rounded-2xl bg-ember-soft p-2 active:cursor-grabbing ${isDragging ? 'opacity-40' : ''} ${compact ? 'w-56' : ''}`}>
        <div className="flex gap-2">
          {entry.image_url && <img src={thumb(entry.image_url, 96)} alt="" draggable={false} className="h-11 w-11 shrink-0 rounded-xl object-cover" />}
          <div className="min-w-0 flex-1">
            <div className="text-[10px] font-bold uppercase tracking-wider text-ember">🫙 Prep</div>
            <Link to={`/recipes/${entry.recipe_id}`} className="line-clamp-2 text-sm font-semibold leading-tight hover:text-ember" {...stop}>{entry.title}</Link>
          </div>
        </div>
        <div className="mt-2 flex items-center justify-between gap-1 text-xs" {...stop}>
          <button onClick={setMade} className="rounded-lg bg-paper/70 px-2 py-1 font-semibold hover:bg-paper" title="Change how much you make">
            makes {Math.round(entry.made_grams ?? 0)} g
          </button>
          <span className={`font-semibold ${(entry.grams_left ?? 0) < 1 ? 'text-stone-500' : 'text-ember'}`}>
            {(entry.grams_left ?? 0) < 1 ? 'all used' : `${Math.round(entry.grams_left!)} g spare`}
          </span>
        </div>
        {shortfall}
        {corner}
      </div>
    )
  }

  // A note ("Dinner at Priya's"): text only. Click to edit; drag to move like a meal.
  if (entry.recipe_id == null && entry.leftover_of == null) {
    if (out) {
      return (
        <div ref={setNodeRef} style={{ transform: CSS.Transform.toString(transform), transition }} {...attributes} {...listeners}
          className={`group relative ${noCallout} cursor-grab touch-manipulation rounded-2xl bg-danger/10 p-2.5 ring-1 ring-danger/30 active:cursor-grabbing ${isDragging ? 'opacity-40' : ''}`}>
          <div className="text-sm font-semibold text-danger">🍽 {entry.title}</div>
          {logRow}
        {corner}
        </div>
      )
    }
    const edit = () => {
      const t = prompt('Note', entry.title)
      if (t == null) return
      if (!t.trim()) { if (confirm('Remove this note?')) remove.mutate(); return }
      optimistic((e) => ({ ...e, title: t.trim() }))
      update.mutate({ title: t.trim() })
    }
    return (
      <div ref={setNodeRef} style={{ transform: CSS.Transform.toString(transform), transition }} {...attributes} {...listeners}
        className={`group relative ${noCallout} cursor-grab touch-manipulation rounded-2xl bg-yellow-soft p-2.5 active:cursor-grabbing ${isDragging ? 'opacity-40' : ''} ${compact ? 'w-56' : ''}`}>
        <button {...stop} onClick={edit} className="block w-full text-left text-sm text-ink">📝 {entry.title}</button>
        {logRow}
        {corner}
      </div>
    )
  }

  return (
    <div ref={setNodeRef} style={{ transform: CSS.Transform.toString(transform), transition }} {...attributes} {...listeners}
      className={`group relative ${noCallout} cursor-grab touch-manipulation rounded-2xl p-2 active:cursor-grabbing ${
        out ? 'bg-danger/10 ring-1 ring-danger/30' : entry.leftover_of ? 'bg-sky-soft' : isBatch ? 'bg-amber-soft' : 'bg-cream'} ${isDragging ? 'opacity-40' : ''} ${compact ? 'w-56' : ''}`}>
      <div className="flex gap-2">
        {entry.image_url && <img src={thumb(entry.image_url, 96)} alt="" draggable={false} className="h-11 w-11 shrink-0 rounded-xl object-cover" />}
        <div className="min-w-0 flex-1">
          {out && <div className="text-[10px] font-bold uppercase tracking-wider text-danger">Ate out · in the fridge</div>}
          {entry.leftover_of && !out && <div className="text-[10px] font-bold uppercase tracking-wider text-sky-deep">Leftovers</div>}
          {isBatch && <div className="text-[10px] font-bold uppercase tracking-wider text-amber-deep">Batch cook</div>}
          {entry.recipe_id ? (
            <Link to={`/recipes/${entry.recipe_id}`} className="line-clamp-2 text-sm font-semibold leading-tight hover:text-ember" {...stop}>{entry.title}</Link>
          ) : (
            <span className="line-clamp-2 text-sm font-semibold leading-tight">{entry.title}</span>
          )}
        </div>
      </div>
      <div className="mt-2 flex items-center justify-between gap-1" {...stop}>
        <Stepper value={entry.servings} step={0.5} min={0.5} onChange={setServings} label="eat" />
        {entry.kcal != null && <span className="whitespace-nowrap text-xs tabular-nums text-stone-500">{Math.round(entry.kcal)}</span>}
      </div>
      {isBatch ? (
        <div {...stop}>
          <div className="mt-1"><Stepper value={entry.cook_portions!} min={entry.servings} onChange={setCook} label="cook" tone="amber" /></div>
          <div className={`mt-1.5 flex items-center justify-between rounded-xl bg-paper/70 px-2 py-1 text-[11px] font-semibold ${entry.portions_left! < 0 ? 'text-red-700' : 'text-amber-deep'}`}>
            {entry.portions_left! < 0 ? `${num(-entry.portions_left!)} more planned than cooked` : `${plural(entry.portions_left!, 'portion')} for later`}
            <button className="hidden text-stone-400 hover:text-ink group-hover:inline pointer-coarse:inline" title="Not a batch" onClick={() => setCook(null)}>✕</button>
          </div>
          {shortfall}
        </div>
      ) : (
        !out && entry.recipe_id != null && !entry.leftover_of && (
          <button {...stop} onClick={() => setCook(entry.servings + 3)}
            className="mt-1 hidden text-xs font-semibold text-amber-deep hover:underline group-hover:block pointer-coarse:block">+ batch cook</button>
        )
      )}
      {!isBatch && shortfall}
      {logRow}
        {corner}
    </div>
  )
}

function Sidebar() {
  const [tab, setTab] = useState<'recipes' | 'fridge'>('recipes')
  const batches = useQuery({ queryKey: ['batches'], queryFn: api.batches })
  return (
    <aside className="order-last rounded-3xl bg-paper p-3 lg:order-none lg:sticky lg:top-24 lg:flex lg:max-h-[calc(100vh-7rem)] lg:flex-col lg:overflow-hidden">
      <div className="mb-3 flex gap-1.5">
        <Chip selected={tab === 'recipes'} onClick={() => setTab('recipes')}>Recipes</Chip>
        <Chip selected={tab === 'fridge'} onClick={() => setTab('fridge')}>Fridge{batches.data?.length ? ` · ${batches.data.length}` : ''}</Chip>
      </div>
      {tab === 'recipes' ? <RecipePicker /> : <FridgePicker batches={batches.data ?? []} />}
    </aside>
  )
}

function FridgePicker({ batches }: { batches: PlanEntry[] }) {
  return (
    <div className="max-h-72 space-y-1 overflow-auto lg:max-h-none lg:flex-1">
      {batches.length === 0 && <p className="p-2 text-sm text-stone-500">No batches with portions left. Use “+ batch cook” on a planned meal.</p>}
      {batches.map((b) => <DraggableBatch key={b.id} batch={b} />)}
      {batches.length > 0 && <p className="p-2 text-xs text-stone-400">Drag onto a day, or tap, to plan leftovers.</p>}
    </div>
  )
}

function DraggableBatch({ batch }: { batch: PlanEntry }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id: `b:${batch.id}`, data: { kind: 'batch', batch } satisfies Drag })
  const pick = useContext(Place)
  return (
    <div ref={setNodeRef} {...attributes} {...listeners} aria-label={`Plan leftovers: ${batch.title}`}
      onClick={() => pick({ kind: 'batch', batch })} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick({ kind: 'batch', batch }) } }}
      onContextMenu={(e) => e.preventDefault()}
      className={`flex ${noCallout} cursor-grab touch-manipulation items-center gap-2 rounded-2xl p-1.5 hover:bg-sand ${isDragging ? 'opacity-40' : ''}`}>
      {batch.image_url && <img src={thumb(batch.image_url, 80)} alt="" draggable={false} className="h-10 w-10 shrink-0 rounded-xl object-cover" />}
      <div className="min-w-0 flex-1">
        <div className="line-clamp-1 text-sm font-semibold leading-tight">{batch.title}</div>
        <div className="text-xs text-stone-500">{batch.day ? `cooked ${dayLabel(batch.day).date}` : 'not scheduled'}</div>
      </div>
      <span className="shrink-0 font-display font-bold tabular-nums text-amber-deep">{num(batch.portions_left ?? 0)}</span>
    </div>
  )
}

function RecipePicker() {
  const [q, setQ] = useState('')
  const dq = useDebounced(q)
  const recipes = useQuery({ queryKey: ['recipes', dq, '', ''], queryFn: () => api.recipes({ q: dq }), placeholderData: (p) => p })
  return (
    <>
      <input className="mb-2 w-full rounded-full bg-cream px-4 py-2.5 text-sm outline-none ring-1 ring-stone-200 placeholder:text-stone-400 focus:ring-2 focus:ring-ember-bright/50"
        placeholder="Find a recipe…" value={q} onChange={(e) => setQ(e.target.value)} />
      <div className="max-h-72 space-y-1 overflow-auto lg:max-h-none lg:flex-1">
        {recipes.data?.map((r) => <DraggableRecipe key={r.id} recipe={r} />)}
      </div>
    </>
  )
}

function DraggableRecipe({ recipe }: { recipe: RecipeSummary }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id: `r:${recipe.id}`, data: { kind: 'recipe', recipe } satisfies Drag })
  const pick = useContext(Place)
  return (
    <div ref={setNodeRef} {...attributes} {...listeners} aria-label={`Add to the plan: ${recipe.title}`}
      onClick={() => pick({ kind: 'recipe', recipe })} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick({ kind: 'recipe', recipe }) } }}
      onContextMenu={(e) => e.preventDefault()}
      className={`flex ${noCallout} cursor-grab touch-manipulation items-center gap-2 rounded-2xl p-1.5 hover:bg-sand ${isDragging ? 'opacity-40' : ''}`}>
      {recipe.image_url && <img src={thumb(recipe.image_url, 80)} alt="" draggable={false} className="h-10 w-10 shrink-0 rounded-xl object-cover" />}
      <span className="line-clamp-2 flex-1 text-sm font-semibold leading-tight">{recipe.title}</span>
      {recipe.kcal_per_serving ? <span className="shrink-0 text-xs text-stone-500">{Math.round(recipe.kcal_per_serving)}</span> : null}
    </div>
  )
}

function AddNote({ day, meal }: { day: string | null; meal?: Meal }) {
  const qc = useQueryClient()
  const add = useMutation({ mutationFn: (title: string) => api.addEntry({ day, meal, title }), onSettled: () => refreshPlan(qc) })
  return (
    <button onClick={() => { const t = prompt('Add a note', ''); if (t?.trim()) add.mutate(t.trim()) }}
      className="w-full rounded-xl px-2 py-1 text-left text-xs font-semibold text-stone-400 hover:bg-sand hover:text-ink">＋ Note</button>
  )
}
