import {
  DndContext,
  DragOverlay,
  PointerSensor,
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
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, type Plan, type PlanEntry, type RecipeSummary } from '../api'
import { addDays, dayLabel, today, weekStart } from '../dates'
import { refreshPlan } from '../plan'
import { Button, Chip, PageHeader, Shimmer, Stepper } from '../components/ui'
import { kcal, num, plural, thumb } from '../format'
import { useDebounced } from '../useDebounced'

const QUEUE = 'queue'
// Drop where the pointer is; fall back to overlap when it's between columns.
const collision: CollisionDetection = (args) => {
  const hits = pointerWithin(args)
  return hits.length ? hits : rectIntersection(args)
}

type Drag =
  | { kind: 'recipe'; recipe: RecipeSummary }
  | { kind: 'batch'; batch: PlanEntry }
  | { kind: 'entry'; entry: PlanEntry }


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
  // Week: all seven days side by side. Day: one day, big (like the app's two views).
  const [view, setView] = useState<'week' | 'day'>('week')
  const [picked, setPicked] = useState<string | null>(null)

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 6 } }),
  )

  const refresh = () => refreshPlan(qc)
  const add = useMutation({ mutationFn: api.addEntry, onSettled: refresh })
  const move = useMutation({
    mutationFn: ({ id, day, position }: { id: number; day: string | null; position: number }) =>
      api.updateEntry(id, { day, position }),
    onSettled: refresh,
  })
  const grocery = useMutation({
    mutationFn: () => api.generateGrocery(start, addDays(start, 6), includeQueue),
    onSuccess: (items) => {
      qc.setQueryData(['grocery'], items)
      navigate('/grocery')
    },
  })

  const columns = (p: Plan): Record<string, PlanEntry[]> => ({ ...p.days, [QUEUE]: p.queue })

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

  const onDragStart = (e: DragStartEvent) => setDragging((e.active.data.current as Drag) ?? null)

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    setDragging(null)
    const p = plan.data
    if (!over || !p) return
    const target = locate(String(over.id), p)
    if (!target) return
    const day = target.col === QUEUE ? null : target.col
    const drag = active.data.current as Drag

    if (drag.kind === 'recipe') {
      add.mutate({ day, recipe_id: drag.recipe.id, servings: 1, position: target.index })
      return
    }
    if (drag.kind === 'batch') {
      add.mutate({ day, leftover_of: drag.batch.id, servings: 1, position: target.index })
      return
    }
    // Move the entry locally first so the drop feels instant, then save.
    const entry = drag.entry
    const cols = columns(p)
    const from = entry.day ?? QUEUE
    const next: Record<string, PlanEntry[]> = Object.fromEntries(Object.entries(cols).map(([k, v]) => [k, [...v]]))
    next[from] = next[from].filter((e) => e.id !== entry.id)
    next[target.col].splice(target.index, 0, { ...entry, day })
    qc.setQueryData<Plan>(planKey, {
      days: Object.fromEntries(Object.keys(p.days).map((d) => [d, next[d]])),
      queue: next[QUEUE],
    })
    move.mutate({ id: entry.id, day, position: target.index })
  }

  const days = plan.data ? Object.keys(plan.data.days) : []
  const weekTotal = days.reduce((sum, d) => sum + dayTotal(plan.data!.days[d]), 0)

  return (
    <DndContext sensors={sensors} collisionDetection={collision} onDragStart={onDragStart} onDragEnd={onDragEnd}
      onDragCancel={() => setDragging(null)}>
      <div className="rise">
        <PageHeader title="Plan" subtitle={`Week of ${dayLabel(start).date} · ${kcal(weekTotal)}`} />
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
          <div className="ml-auto flex items-center gap-3">
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
                  <div className="mb-3 grid grid-cols-7 gap-2">
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
            {dragging.kind === 'recipe' ? dragging.recipe.title : dragging.kind === 'batch' ? `Leftovers: ${dragging.batch.title}` : dragging.entry.title}
          </div>
        )}
      </DragOverlay>
    </DndContext>
  )
}

const dayTotal = (entries: PlanEntry[]) => entries.reduce((s, e) => s + (e.kcal ?? 0), 0)

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
  const { setNodeRef, isOver } = useDroppable({ id: `col:${id}` })
  return (
    <div ref={setNodeRef}
      className={`rounded-3xl p-2.5 transition ${isOver ? 'bg-ember-soft ring-2 ring-ember-bright' : 'bg-paper'} ${highlight && !isOver ? 'ring-2 ring-ember-bright' : ''}`}>
      <div className="mb-2 flex items-baseline justify-between gap-1 px-1.5">
        <span className="whitespace-nowrap">
          <span className={`font-display text-lg font-bold ${highlight ? 'text-ember-bright' : ''}`}>{title}</span>
          <span className="ml-1.5 text-sm text-stone-500">{subtitle}</span>
        </span>
        {total ? <span className="whitespace-nowrap font-display text-sm font-bold tabular-nums text-ember">{Math.round(total)}</span> : null}
      </div>
      <SortableContext items={entries.map((e) => `e:${e.id}`)} strategy={verticalListSortingStrategy}>
        <div className={horizontal ? 'flex min-h-14 flex-wrap gap-2' : wide ? 'grid min-h-40 gap-3 sm:grid-cols-2 xl:grid-cols-3' : 'min-h-28 space-y-2'}>
          {entries.map((e) => <EntryCard key={e.id} entry={e} compact={horizontal} />)}
          {entries.length === 0 && <div className="grid min-h-20 place-items-center rounded-2xl border-2 border-dashed border-stone-200 px-2 text-center text-xs text-stone-400">Drop recipes here</div>}
          <AddNote day={id === QUEUE ? null : id} />
        </div>
      </SortableContext>
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
  const isBatch = entry.cook_portions != null
  const stop = { onPointerDown: (e: React.PointerEvent) => e.stopPropagation() }

  // A note ("Dinner at Priya's"): text only. Click to edit; drag to move like a meal.
  if (entry.recipe_id == null && entry.leftover_of == null) {
    const edit = () => {
      const t = prompt('Note', entry.title)
      if (t == null) return
      if (!t.trim()) { if (confirm('Remove this note?')) remove.mutate(); return }
      optimistic((e) => ({ ...e, title: t.trim() }))
      update.mutate({ title: t.trim() })
    }
    return (
      <div ref={setNodeRef} style={{ transform: CSS.Transform.toString(transform), transition }} {...attributes} {...listeners}
        className={`group relative cursor-grab touch-manipulation rounded-2xl bg-yellow-soft p-2.5 active:cursor-grabbing ${isDragging ? 'opacity-40' : ''} ${compact ? 'w-56' : ''}`}>
        <button {...stop} onClick={edit} className="block w-full text-left text-sm text-ink">📝 {entry.title}</button>
        <button {...stop} onClick={() => remove.mutate()} title="Remove note"
          className="press absolute -right-1.5 -top-1.5 hidden h-6 w-6 rounded-full bg-ink text-xs text-cream group-hover:grid group-hover:place-items-center">✕</button>
      </div>
    )
  }

  return (
    <div ref={setNodeRef} style={{ transform: CSS.Transform.toString(transform), transition }} {...attributes} {...listeners}
      className={`group relative cursor-grab touch-manipulation rounded-2xl p-2 active:cursor-grabbing ${
        entry.leftover_of ? 'bg-sky-soft' : isBatch ? 'bg-amber-soft' : 'bg-cream'} ${isDragging ? 'opacity-40' : ''} ${compact ? 'w-56' : ''}`}>
      <div className="flex gap-2">
        {entry.image_url && <img src={thumb(entry.image_url, 96)} alt="" className="h-11 w-11 shrink-0 rounded-xl object-cover" />}
        <div className="min-w-0 flex-1">
          {entry.leftover_of && <div className="text-[10px] font-bold uppercase tracking-wider text-sky-deep">Leftovers</div>}
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
            <button className="hidden text-stone-400 hover:text-ink group-hover:inline" title="Not a batch" onClick={() => setCook(null)}>✕</button>
          </div>
        </div>
      ) : (
        entry.recipe_id != null && !entry.leftover_of && (
          <button {...stop} onClick={() => setCook(entry.servings + 3)}
            className="mt-1 hidden text-xs font-semibold text-amber-deep hover:underline group-hover:block">+ batch cook</button>
        )
      )}
      <button {...stop} onClick={() => remove.mutate()} title={isBatch ? 'Remove (and its leftovers)' : 'Remove'}
        className="press absolute -right-1.5 -top-1.5 hidden h-6 w-6 rounded-full bg-ink text-xs text-cream group-hover:grid group-hover:place-items-center">✕</button>
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
      {batches.length > 0 && <p className="p-2 text-xs text-stone-400">Drag onto a day to plan leftovers.</p>}
    </div>
  )
}

function DraggableBatch({ batch }: { batch: PlanEntry }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id: `b:${batch.id}`, data: { kind: 'batch', batch } satisfies Drag })
  return (
    <div ref={setNodeRef} {...attributes} {...listeners}
      className={`flex cursor-grab touch-manipulation items-center gap-2 rounded-2xl p-1.5 hover:bg-sand ${isDragging ? 'opacity-40' : ''}`}>
      {batch.image_url && <img src={thumb(batch.image_url, 80)} alt="" className="h-10 w-10 shrink-0 rounded-xl object-cover" />}
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
        placeholder="Find a recipe to drag…" value={q} onChange={(e) => setQ(e.target.value)} />
      <div className="max-h-72 space-y-1 overflow-auto lg:max-h-none lg:flex-1">
        {recipes.data?.map((r) => <DraggableRecipe key={r.id} recipe={r} />)}
      </div>
    </>
  )
}

function DraggableRecipe({ recipe }: { recipe: RecipeSummary }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id: `r:${recipe.id}`, data: { kind: 'recipe', recipe } satisfies Drag })
  return (
    <div ref={setNodeRef} {...attributes} {...listeners}
      className={`flex cursor-grab touch-manipulation items-center gap-2 rounded-2xl p-1.5 hover:bg-sand ${isDragging ? 'opacity-40' : ''}`}>
      {recipe.image_url && <img src={thumb(recipe.image_url, 80)} alt="" className="h-10 w-10 shrink-0 rounded-xl object-cover" />}
      <span className="line-clamp-2 flex-1 text-sm font-semibold leading-tight">{recipe.title}</span>
      {recipe.kcal_per_serving ? <span className="shrink-0 text-xs text-stone-500">{Math.round(recipe.kcal_per_serving)}</span> : null}
    </div>
  )
}

function AddNote({ day }: { day: string | null }) {
  const qc = useQueryClient()
  const add = useMutation({ mutationFn: (title: string) => api.addEntry({ day, title }), onSettled: () => refreshPlan(qc) })
  return (
    <button onClick={() => { const t = prompt('Add a note', ''); if (t?.trim()) add.mutate(t.trim()) }}
      className="w-full rounded-xl px-2 py-1 text-left text-xs font-semibold text-stone-400 hover:bg-sand hover:text-ink">＋ Note</button>
  )
}
