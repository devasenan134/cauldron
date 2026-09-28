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
import { Button, inputCls, kcal, thumb } from '../components/ui'
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
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <Button variant="ghost" onClick={() => setWeek(addDays(start, -7))}>←</Button>
        <Button variant="ghost" onClick={() => setWeek(weekStart(today()))}>This week</Button>
        <Button variant="ghost" onClick={() => setWeek(addDays(start, 7))}>→</Button>
        <h1 className="ml-2 text-lg font-semibold">
          Week of {dayLabel(start).date}
          <span className="ml-3 text-sm font-normal text-stone-500">{kcal(weekTotal)} planned</span>
        </h1>
        <div className="ml-auto flex items-center gap-3">
          <label className="flex items-center gap-1.5 text-sm text-stone-600">
            <input type="checkbox" checked={includeQueue} onChange={(e) => setIncludeQueue(e.target.checked)} />
            include queue
          </label>
          <Button onClick={() => grocery.mutate()} disabled={grocery.isPending}>Make grocery list</Button>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[220px_minmax(0,1fr)]">
        <Sidebar />
        <div className="space-y-4">
          {plan.isError && <p className="text-red-700">Couldn't load the plan: {String(plan.error)}</p>}
          <Column id={QUEUE} title="Queue" subtitle="planned, no day yet" entries={plan.data?.queue ?? []} horizontal />
          <div className="grid gap-3 md:grid-cols-4 xl:grid-cols-7">
            {days.map((d) => {
              const { weekday, date } = dayLabel(d)
              return (
                <Column key={d} id={d} title={weekday} subtitle={date.replace(/^\D+/, '')} entries={plan.data!.days[d]}
                  highlight={d === today()} total={dayTotal(plan.data!.days[d])} />
              )
            })}
          </div>
        </div>
      </div>

      <DragOverlay>
        {dragging && (
          <div className="w-56 rotate-2 rounded-lg bg-white p-2 text-sm font-medium shadow-xl ring-1 ring-stone-300">
            {dragging.kind === 'recipe' ? dragging.recipe.title : dragging.kind === 'batch' ? `Leftovers: ${dragging.batch.title}` : dragging.entry.title}
          </div>
        )}
      </DragOverlay>
    </DndContext>
  )
}

const dayTotal = (entries: PlanEntry[]) => entries.reduce((s, e) => s + (e.kcal ?? 0), 0)

function Column({ id, title, subtitle, entries, total, highlight, horizontal }: {
  id: string
  title: string
  subtitle: string
  entries: PlanEntry[]
  total?: number
  highlight?: boolean
  horizontal?: boolean
}) {
  const { setNodeRef, isOver } = useDroppable({ id: `col:${id}` })
  return (
    <div
      ref={setNodeRef}
      className={`rounded-xl p-2 ring-1 transition ${isOver ? 'bg-ember-soft ring-ember' : 'bg-white ring-stone-200'} ${
        highlight ? 'ring-2 ring-ink' : ''
      }`}
    >
      <div className="mb-2 flex items-baseline justify-between gap-1 px-1">
        <span className="whitespace-nowrap text-sm font-semibold">
          {title}
          <span className="ml-1 font-normal text-stone-500">{subtitle}</span>
        </span>
        {total ? <span className="whitespace-nowrap text-xs font-medium tabular-nums text-ember">{Math.round(total)}</span> : null}
      </div>
      <SortableContext items={entries.map((e) => `e:${e.id}`)} strategy={verticalListSortingStrategy}>
        <div className={horizontal ? 'flex min-h-14 flex-wrap gap-2' : 'min-h-24 space-y-2'}>
          {entries.map((e) => <EntryCard key={e.id} entry={e} compact={horizontal} />)}
          {entries.length === 0 && <div className="px-1 py-3 text-xs text-stone-400">Drop recipes here</div>}
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
  const refresh = () => refreshPlan(qc)
  const update = useMutation({
    mutationFn: (patch: Parameters<typeof api.updateEntry>[1]) => api.updateEntry(entry.id, patch),
    onSettled: refresh,
  })
  const remove = useMutation({ mutationFn: () => api.deleteEntry(entry.id), onSettled: refresh })
  const isBatch = entry.cook_portions != null
  const stop = { onPointerDown: (e: React.PointerEvent) => e.stopPropagation() }
  const step = 'rounded px-1 hover:bg-stone-200'

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      {...attributes}
      {...listeners}
      className={`group relative cursor-grab touch-manipulation rounded-lg p-1.5 ring-1 active:cursor-grabbing ${
        entry.leftover_of ? 'bg-sky-50 ring-sky-200' : isBatch ? 'bg-amber-50 ring-amber-200' : 'bg-stone-50 ring-stone-200'
      } ${isDragging ? 'opacity-40' : ''} ${compact ? 'w-48' : ''}`}
    >
      <div className="flex gap-1.5">
        {entry.image_url && <img src={thumb(entry.image_url, 64)} alt="" className="h-8 w-8 shrink-0 rounded object-cover" />}
        <div className="min-w-0 flex-1">
          {entry.leftover_of && <div className="text-[10px] font-semibold uppercase tracking-wide text-sky-700">Leftovers</div>}
          {entry.recipe_id ? (
            <Link to={`/recipes/${entry.recipe_id}`} className="line-clamp-2 text-xs font-medium leading-tight hover:text-ember" {...stop}>
              {entry.title}
            </Link>
          ) : (
            <span className="line-clamp-2 text-xs font-medium leading-tight">{entry.title}</span>
          )}
        </div>
      </div>
      <div className="mt-1 flex items-center gap-0.5 text-[11px] text-stone-500" {...stop}>
        {isBatch && <span className="mr-0.5">eat</span>}
        <button className={step} onClick={() => update.mutate({ servings: Math.max(0.5, entry.servings - 0.5) })}>−</button>
        <span className="tabular-nums">{entry.servings}×</span>
        <button className={step} onClick={() => update.mutate({ servings: entry.servings + 0.5 })}>+</button>
        {entry.kcal != null && (
          <span className="ml-auto whitespace-nowrap tabular-nums">{isBatch ? Math.round(entry.kcal) : kcal(entry.kcal)}</span>
        )}
      </div>
      {isBatch ? (
        <div className="mt-0.5 flex items-center gap-0.5 text-[11px] text-amber-800" {...stop}>
          <span className="mr-0.5">cook</span>
          <button className={step} onClick={() => update.mutate({ cook_portions: Math.max(entry.servings, entry.cook_portions! - 1) })}>−</button>
          <span className="tabular-nums">{entry.cook_portions}</span>
          <button className={step} onClick={() => update.mutate({ cook_portions: entry.cook_portions! + 1 })}>+</button>
          <span className={`ml-auto whitespace-nowrap tabular-nums ${entry.portions_left! < 0 ? 'font-semibold text-red-700' : ''}`}>
            {entry.portions_left} left
          </span>
          <button className={`${step} hidden text-stone-400 group-hover:inline`} title="Not a batch" onClick={() => update.mutate({ cook_portions: null })}>×</button>
        </div>
      ) : (
        entry.recipe_id != null &&
        !entry.leftover_of && (
          <button
            {...stop}
            onClick={() => update.mutate({ cook_portions: entry.servings + 3 })}
            className="mt-0.5 hidden text-[11px] text-amber-700 hover:underline group-hover:block"
          >
            + batch cook
          </button>
        )
      )}
      <button
        {...stop}
        onClick={() => remove.mutate()}
        className="absolute -right-1.5 -top-1.5 hidden h-5 w-5 rounded-full bg-ink text-xs text-cream group-hover:block"
        title={isBatch ? 'Remove (and its leftovers)' : 'Remove'}
      >
        ×
      </button>
    </div>
  )
}

function Sidebar() {
  const [tab, setTab] = useState<'recipes' | 'fridge'>('recipes')
  const batches = useQuery({ queryKey: ['batches'], queryFn: api.batches })
  const tabCls = (t: typeof tab) =>
    `flex-1 rounded-md px-2 py-1 text-xs font-medium ${tab === t ? 'bg-ink text-cream' : 'text-stone-600 hover:bg-stone-100'}`
  return (
    <aside className="rounded-xl bg-white p-2 ring-1 ring-stone-200 lg:sticky lg:top-20 lg:max-h-[calc(100vh-6rem)] lg:overflow-hidden lg:flex lg:flex-col">
      <div className="mb-2 flex gap-1">
        <button className={tabCls('recipes')} onClick={() => setTab('recipes')}>Recipes</button>
        <button className={tabCls('fridge')} onClick={() => setTab('fridge')}>
          Fridge{batches.data?.length ? ` · ${batches.data.length}` : ''}
        </button>
      </div>
      {tab === 'recipes' ? <RecipePicker /> : <FridgePicker batches={batches.data ?? []} />}
    </aside>
  )
}

function FridgePicker({ batches }: { batches: PlanEntry[] }) {
  return (
    <div className="max-h-72 space-y-1 overflow-auto lg:max-h-none lg:flex-1">
      {batches.length === 0 && (
        <p className="p-2 text-xs text-stone-500">No batches with portions left. Use “+ batch cook” on a planned meal.</p>
      )}
      {batches.map((b) => <DraggableBatch key={b.id} batch={b} />)}
      {batches.length > 0 && <p className="p-1 text-[11px] text-stone-400">Drag onto a day to plan leftovers.</p>}
    </div>
  )
}

function DraggableBatch({ batch }: { batch: PlanEntry }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `b:${batch.id}`,
    data: { kind: 'batch', batch } satisfies Drag,
  })
  return (
    <div
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      className={`flex cursor-grab touch-manipulation items-center gap-2 rounded-lg p-1 hover:bg-stone-100 ${isDragging ? 'opacity-40' : ''}`}
    >
      {batch.image_url && <img src={thumb(batch.image_url, 64)} alt="" className="h-8 w-8 shrink-0 rounded object-cover" />}
      <div className="min-w-0 flex-1">
        <div className="line-clamp-1 text-xs font-medium leading-tight">{batch.title}</div>
        <div className="text-[11px] text-stone-500">{batch.day ? `cooked ${dayLabel(batch.day).date}` : 'not scheduled'}</div>
      </div>
      <span className="shrink-0 text-xs font-semibold tabular-nums text-amber-800">{batch.portions_left}</span>
    </div>
  )
}

function RecipePicker() {
  const [q, setQ] = useState('')
  const dq = useDebounced(q)
  const recipes = useQuery({ queryKey: ['recipes', dq, '', ''], queryFn: () => api.recipes({ q: dq }), placeholderData: (p) => p })
  return (
    <>
      <input className={`${inputCls} mb-2 w-full`} placeholder="Find a recipe to drag…" value={q} onChange={(e) => setQ(e.target.value)} />
      <div className="max-h-72 space-y-1 overflow-auto lg:max-h-none lg:flex-1">
        {recipes.data?.map((r) => <DraggableRecipe key={r.id} recipe={r} />)}
      </div>
    </>
  )
}

function DraggableRecipe({ recipe }: { recipe: RecipeSummary }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `r:${recipe.id}`,
    data: { kind: 'recipe', recipe } satisfies Drag,
  })
  return (
    <div
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      className={`flex cursor-grab touch-manipulation items-center gap-2 rounded-lg p-1 hover:bg-stone-100 ${isDragging ? 'opacity-40' : ''}`}
    >
      {recipe.image_url && <img src={thumb(recipe.image_url, 64)} alt="" className="h-8 w-8 shrink-0 rounded object-cover" />}
      <span className="line-clamp-2 flex-1 text-xs font-medium leading-tight">{recipe.title}</span>
      {recipe.kcal_per_serving ? <span className="shrink-0 text-[11px] text-stone-500">{Math.round(recipe.kcal_per_serving)}</span> : null}
    </div>
  )
}
