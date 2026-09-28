import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type Ingredient, type RecipeDetail as Recipe } from '../api'
import { today } from '../dates'
import { refreshPlan } from '../plan'
import { Button, DayChips, Pill, Shimmer, Stepper } from '../components/ui'
import { dayChipLabel, kcal, num, plural, thumb } from '../format'
import { useDebounced } from '../useDebounced'

const SOURCE_NOTE: Record<string, string> = {
  given: 'weight from the recipe',
  parts: 'worked out from the other parts',
  portion: 'count × typical weight',
  estimate: 'typical amount (estimate)',
  manual: 'set by you',
}

// Protein, carbs and fat: the same colours as the app.
const MACRO = { protein: '#60a5fa', carbs: '#fbbf24', fat: '#fb923c' }

export default function RecipeDetail() {
  const id = Number(useParams().id)
  const recipe = useQuery({ queryKey: ['recipe', id], queryFn: () => api.recipe(id) })

  if (recipe.isPending) return (
    <div className="grid gap-8 lg:grid-cols-2">
      <Shimmer className="aspect-square rounded-[32px]" />
      <div><Shimmer className="h-12 w-4/5 rounded-xl" /><Shimmer className="mt-4 h-6 w-1/2 rounded-xl" /><Shimmer className="mt-8 h-56 rounded-3xl" /></div>
    </div>
  )
  if (recipe.isError) return <p className="text-red-700">Couldn't load recipe: {String(recipe.error)}</p>
  const r = recipe.data

  const groups = new Map<string, Ingredient[]>()
  for (const ing of r.ingredients) {
    const g = ing.group ?? ''
    groups.set(g, [...(groups.get(g) ?? []), ing])
  }

  return (
    <div className="rise">
      <Link to="/recipes" className="press mb-4 inline-flex items-center gap-1 rounded-full bg-paper px-4 py-2 text-sm font-semibold">← Recipes</Link>

      <section className="grid gap-8 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
        <div className="relative overflow-hidden rounded-[32px] bg-sand lg:sticky lg:top-24 lg:self-start">
          {r.image_url && <img src={thumb(r.image_url, 1000, 1000)} alt="" className="aspect-square w-full object-cover" />}
        </div>
        <div>
          <h1 className="font-display text-4xl font-extrabold leading-tight tracking-tight sm:text-5xl">{r.title}</h1>
          <div className="mt-4 flex flex-wrap gap-2">
            {r.total_minutes ? <Pill tone="paper">⏱ {r.total_minutes} min</Pill> : null}
            {(r.yield_text || r.servings) && <Pill tone="paper">🍴 {r.yield_text ?? plural(r.servings!, 'serving')}</Pill>}
            {r.cuisine && <Pill tone="paper">{r.cuisine}</Pill>}
            {r.category && <Pill tone="paper">{r.category}</Pill>}
            {r.tags.map((t) => <Pill key={t}>{t}</Pill>)}
          </div>
          {r.description && <p className="mt-4 whitespace-pre-line text-lg text-stone-700">{r.description}</p>}
          <div className="mt-5 flex flex-wrap items-center gap-3">
            {r.video_url && <a href={r.video_url} target="_blank" rel="noreferrer"><Button variant="soft">▶ Watch video</Button></a>}
            {r.source_url && <a className="text-sm text-stone-500 hover:underline" href={r.source_url} target="_blank" rel="noreferrer">Original{r.author ? ` by ${r.author}` : ''}</a>}
          </div>
          <NutritionCard r={r} />
          <AddToPlan r={r} />
        </div>
      </section>

      <section className="mt-12 grid gap-10 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
        <div>
          <div className="mb-2 flex items-baseline">
            <h2 className="flex-1 font-display text-3xl font-bold">Ingredients</h2>
            <span className="font-semibold text-stone-500">{r.ingredients.length}</span>
          </div>
          {r.can_edit && <p className="mb-3 text-sm text-stone-500">Click a weight to correct it, or a food to change what it's counted as.</p>}
          {[...groups].map(([group, ings]) => (
            <div key={group} className="mb-4">
              {group && <h3 className="mb-2 ml-1 text-xs font-bold uppercase tracking-widest text-ember">{group}</h3>}
              <ul className="divide-y divide-stone-100 rounded-3xl bg-paper">
                {ings.map((ing) => <IngredientRow key={ing.id} ing={ing} recipeId={r.id} editable={r.can_edit} />)}
              </ul>
            </div>
          ))}
        </div>
        <div>
          <h2 className="mb-4 font-display text-3xl font-bold">Steps</h2>
          <ol className="space-y-3">
            {r.steps.map((s, i) => (
              <li key={s.id} className="flex gap-3">
                <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-ember-bright font-display font-bold text-white">{i + 1}</span>
                <div className="flex-1 rounded-3xl bg-paper p-5">
                  {s.title && <p className="font-display text-lg font-bold">{s.title}</p>}
                  <p className="mt-1 whitespace-pre-line leading-relaxed text-stone-700">{s.text}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>
    </div>
  )
}

function NutritionCard({ r }: { r: Recipe }) {
  const n = r.nutrition
  const m = n.per_serving ?? n.total
  // Where the calories come from: protein and carbs 4 kcal/g, fat 9.
  const parts = { protein: m.protein * 4, carbs: m.carbs * 4, fat: m.fat * 9 }
  const total = parts.protein + parts.carbs + parts.fat || 1
  return (
    <div className="mt-6 rounded-3xl bg-ink p-6 text-cream">
      <p className="text-sm text-cream/60">{n.per_serving ? `Per serving · recipe makes ${num(r.servings ?? 1)}` : 'Whole recipe'}</p>
      <p className="mt-1 flex items-baseline gap-2">
        <span className="font-display text-5xl font-extrabold text-white">{Math.round(m.kcal).toLocaleString()}</span>
        <span className="text-cream/60">kcal</span>
      </p>
      <div className="mt-4 flex h-2.5 overflow-hidden rounded-full">
        {(Object.keys(parts) as (keyof typeof parts)[]).map((k) => (
          <div key={k} style={{ width: `${(parts[k] / total) * 100}%`, background: MACRO[k] }} />
        ))}
      </div>
      <div className="mt-4 grid grid-cols-3">
        {([['protein', 'Protein', m.protein], ['carbs', 'Carbs', m.carbs], ['fat', 'Fat', m.fat]] as const).map(([k, label, g]) => (
          <div key={k} className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full" style={{ background: MACRO[k] }} />
            <div>
              <p className="font-display text-lg font-bold text-white">{Math.round(g)} g</p>
              <p className="text-xs text-cream/60">{label}</p>
            </div>
          </div>
        ))}
      </div>
      {n.per_serving && <p className="mt-4 text-xs text-cream/50">Whole recipe: {kcal(n.total.kcal)}</p>}
      {n.left_out.length > 0 && <p className="mt-1 text-xs text-orange-300">Not counted (no amount): {n.left_out.join(', ')}</p>}
      {n.estimated.length > 0 && <p className="mt-1 text-xs text-cream/50">Estimated: {n.estimated.join(', ')}</p>}
    </div>
  )
}

function AddToPlan({ r }: { r: Recipe }) {
  const qc = useQueryClient()
  const [day, setDay] = useState<string | null>(today())
  const [cook, setCook] = useState(r.servings ?? 1)
  const [eat, setEat] = useState(1)
  const extra = cook - eat
  const add = useMutation({
    // Cooking more than you eat makes a batch; the rest goes in the fridge.
    mutationFn: () => api.addEntry({ day, recipe_id: r.id, servings: eat, cook_portions: extra > 0 ? cook : null }),
    onSuccess: () => refreshPlan(qc),
  })
  return (
    <div className="mt-6 rounded-3xl bg-paper p-6">
      <h3 className="font-display text-2xl font-bold">Add to plan</h3>
      <p className="mb-3 mt-4 text-sm font-semibold">When</p>
      <DayChips selected={day} onPick={setDay} days={8} />
      <div className="mt-5 grid grid-cols-2 gap-3">
        <div className="flex flex-col items-center rounded-2xl bg-cream p-3">
          <span className="text-xs text-stone-500">Cook</span>
          <Stepper big value={cook} onChange={(v) => { setCook(v); if (eat > v) setEat(v) }} label="cook" />
          <span className="text-[11px] text-stone-500">portions</span>
        </div>
        <div className="flex flex-col items-center rounded-2xl bg-cream p-3">
          <span className="text-xs text-stone-500">Eat now</span>
          <Stepper big value={eat} step={0.5} min={0.5} onChange={(v) => { setEat(v); if (cook < v) setCook(v) }} label="eat" />
          <span className="text-[11px] text-stone-500">portions</span>
        </div>
      </div>
      <p className={`mt-4 rounded-2xl p-3 text-sm ${extra > 0 ? 'bg-amber-soft font-semibold text-amber-deep' : 'bg-cream text-stone-500'}`}>
        {extra > 0 ? `🥡 Batch cook: ${plural(extra, 'portion')} go${extra === 1 ? 'es' : ''} in the fridge for later.` : 'Cook more than you eat to batch cook, and the rest goes in the fridge.'}
      </p>
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Button onClick={() => add.mutate()} disabled={add.isPending} className="px-8 py-3 text-base">
          {day === null ? 'Add to queue' : `Add to ${dayChipLabel(day)}`}
        </Button>
        {add.isSuccess && <Link to="/planner" className="rise font-semibold text-ember hover:underline">Added ✓ Open plan →</Link>}
        {add.isError && <span className="text-sm text-red-700">{String(add.error)}</span>}
      </div>
    </div>
  )
}

function usePatchIngredient(recipeId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, patch }: { id: number; patch: { grams?: number | null; food_id?: number | null } }) =>
      api.patchIngredient(id, patch),
    onSuccess: (data) => {
      qc.setQueryData(['recipe', recipeId], data)
      qc.invalidateQueries({ queryKey: ['recipes'] })
      qc.invalidateQueries({ queryKey: ['plan'] })
    },
  })
}

function IngredientRow({ ing, recipeId, editable }: { ing: Ingredient; recipeId: number; editable: boolean }) {
  const patch = usePatchIngredient(recipeId)
  const [editingGrams, setEditingGrams] = useState(false)
  const [pickingFood, setPickingFood] = useState(false)
  const [grams, setGrams] = useState(ing.grams?.toString() ?? '')

  const saveGrams = () => {
    setEditingGrams(false)
    const value = grams.trim() === '' ? null : Number(grams)
    if (value !== ing.grams && (value === null || !Number.isNaN(value))) patch.mutate({ id: ing.id, patch: { grams: value } })
  }

  return (
    <li className="px-4 py-3">
      <div className="flex items-center gap-3">
        <div className="min-w-0 flex-1">
          <p className="font-semibold">{ing.name}{ing.note && <span className="font-normal text-stone-500">, {ing.note}</span>}</p>
          {ing.label && <p className="text-sm text-stone-500">{ing.label}</p>}
          <button onClick={() => setPickingFood((v) => !v)} disabled={!editable} className="text-xs text-stone-400 enabled:hover:text-ember">
            {ing.food_name ?? 'no food linked'}
          </button>
        </div>
        <div className="flex flex-col items-end">
          {editingGrams ? (
            <input autoFocus value={grams} inputMode="decimal" onChange={(e) => setGrams(e.target.value)} onBlur={saveGrams}
              onKeyDown={(e) => e.key === 'Enter' && saveGrams()}
              className="w-20 rounded-lg bg-sand px-2 py-1 text-right font-semibold outline-none ring-2 ring-ember-bright/50" />
          ) : (
            <button onClick={() => setEditingGrams(true)} disabled={!editable}
              title={ing.grams_source ? SOURCE_NOTE[ing.grams_source] : 'not counted: click to add a weight'}
              className={`rounded-lg px-2 py-1 font-semibold tabular-nums ${editable ? 'bg-sand hover:bg-stone-200' : ''} ${
                ing.grams == null ? 'text-amber-deep' : ing.grams_source === 'estimate' ? 'italic text-stone-400' : ''}`}>
              {ing.grams == null ? (editable ? '+ g' : '—') : `${Math.round(ing.grams)} g`}
            </button>
          )}
          <span className="mt-0.5 text-xs tabular-nums text-stone-500">{ing.nutrition ? `${Math.round(ing.nutrition.kcal)} kcal` : ''}</span>
        </div>
      </div>
      {pickingFood && (
        <FoodPicker initial={ing.name} onPick={(foodId) => { setPickingFood(false); patch.mutate({ id: ing.id, patch: { food_id: foodId } }) }} />
      )}
    </li>
  )
}

function FoodPicker({ initial, onPick }: { initial: string; onPick: (id: number) => void }) {
  const [q, setQ] = useState(initial)
  const dq = useDebounced(q)
  const foods = useQuery({ queryKey: ['foods', dq], queryFn: () => api.foods(dq), enabled: dq.trim().length > 1 })
  return (
    <div className="rise mt-3 rounded-2xl bg-cream p-3">
      <input autoFocus value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search foods…"
        className="w-full rounded-full bg-paper px-4 py-2 outline-none ring-1 ring-stone-200 focus:ring-2 focus:ring-ember-bright/50" />
      <ul className="mt-2 max-h-56 overflow-auto">
        {foods.data?.map((f) => (
          <li key={f.id}>
            <button onClick={() => onPick(f.id)} className="flex w-full justify-between gap-2 rounded-xl px-3 py-2 text-left text-sm hover:bg-paper">
              <span>{f.name}</span>
              <span className="shrink-0 text-stone-500">{Math.round(f.kcal)} kcal/100 g</span>
            </button>
          </li>
        ))}
        {foods.data?.length === 0 && <li className="px-3 py-2 text-sm text-stone-500">No match. Try fewer words.</li>}
      </ul>
    </div>
  )
}
