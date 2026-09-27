import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type Ingredient, type RecipeDetail as Recipe } from '../api'
import { today } from '../dates'
import { Button, inputCls, kcal, Pill, thumb } from '../components/ui'
import { useDebounced } from '../useDebounced'

const SOURCE_NOTE: Record<string, string> = {
  given: 'weight from the recipe',
  parts: 'worked out from the other parts',
  portion: 'count × typical weight',
  estimate: 'typical amount (estimate)',
  manual: 'set by you',
}

export default function RecipeDetail() {
  const id = Number(useParams().id)
  const recipe = useQuery({ queryKey: ['recipe', id], queryFn: () => api.recipe(id) })

  if (recipe.isPending) return <p className="text-stone-500">Loading…</p>
  if (recipe.isError) return <p className="text-red-700">Couldn't load recipe: {String(recipe.error)}</p>
  const r = recipe.data

  const groups = new Map<string, Ingredient[]>()
  for (const ing of r.ingredients) {
    const g = ing.group ?? ''
    groups.set(g, [...(groups.get(g) ?? []), ing])
  }

  return (
    <div className="space-y-8">
      <Link to="/recipes" className="text-sm text-stone-500 hover:text-ink">← Recipes</Link>

      <section className="grid gap-6 md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        {r.image_url && (
          <img src={thumb(r.image_url, 800, 600)} alt="" className="aspect-[4/3] w-full rounded-2xl object-cover" />
        )}
        <div className="space-y-4">
          <h1 className="text-3xl font-bold tracking-tight">{r.title}</h1>
          <div className="flex flex-wrap gap-1.5">
            {r.yield_text && <Pill>{r.yield_text}</Pill>}
            {r.total_minutes ? <Pill>{r.total_minutes} min</Pill> : null}
            {r.cuisine && <Pill>{r.cuisine}</Pill>}
            {r.category && <Pill>{r.category}</Pill>}
            {r.tags.map((t) => <Pill key={t}>{t}</Pill>)}
          </div>
          {r.description && <p className="whitespace-pre-line text-stone-700">{r.description}</p>}
          <div className="flex flex-wrap gap-3 text-sm">
            {r.video_url && <a className="font-medium text-ember hover:underline" href={r.video_url} target="_blank" rel="noreferrer">▶ Watch video</a>}
            {r.source_url && <a className="text-stone-500 hover:underline" href={r.source_url} target="_blank" rel="noreferrer">Original recipe{r.author ? ` by ${r.author}` : ''}</a>}
          </div>
          <NutritionPanel r={r} />
          <AddToPlan recipeId={r.id} defaultServings={r.servings ?? 1} />
        </div>
      </section>

      <section className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <div>
          <h2 className="mb-3 text-xl font-semibold">Ingredients</h2>
          <p className="mb-3 text-xs text-stone-500">Click a weight to correct it; click a food to change what it's counted as.</p>
          {[...groups].map(([group, ings]) => (
            <div key={group} className="mb-5">
              {group && <h3 className="mb-1 text-sm font-semibold uppercase tracking-wide text-stone-500">{group}</h3>}
              <ul className="divide-y divide-stone-200 rounded-xl bg-white ring-1 ring-stone-200">
                {ings.map((ing) => <IngredientRow key={ing.id} ing={ing} recipeId={r.id} />)}
              </ul>
            </div>
          ))}
        </div>
        <div>
          <h2 className="mb-3 text-xl font-semibold">Steps</h2>
          <ol className="space-y-4">
            {r.steps.map((s, i) => (
              <li key={s.id} className="rounded-xl bg-white p-4 ring-1 ring-stone-200">
                <div className="mb-1 text-sm font-semibold">
                  <span className="mr-2 text-ember">{i + 1}</span>{s.title}
                </div>
                <p className="whitespace-pre-line text-sm leading-relaxed text-stone-700">{s.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>
    </div>
  )
}

function NutritionPanel({ r }: { r: Recipe }) {
  const n = r.nutrition
  const m = n.per_serving ?? n.total
  return (
    <div className="rounded-xl bg-white p-4 ring-1 ring-stone-200">
      <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-stone-500">
        {n.per_serving ? `Per serving (recipe makes ${r.servings})` : 'Whole recipe'}
      </div>
      <div className="grid grid-cols-4 gap-2 text-center">
        <Stat label="Calories" value={Math.round(m.kcal)} strong />
        <Stat label="Protein" value={`${Math.round(m.protein)} g`} />
        <Stat label="Carbs" value={`${Math.round(m.carbs)} g`} />
        <Stat label="Fat" value={`${Math.round(m.fat)} g`} />
      </div>
      {n.per_serving && <div className="mt-2 text-xs text-stone-500">Whole recipe: {kcal(n.total.kcal)}</div>}
      {n.left_out.length > 0 && (
        <p className="mt-2 text-xs text-amber-700">Not counted (no amount): {n.left_out.join(', ')}</p>
      )}
      {n.estimated.length > 0 && <p className="mt-1 text-xs text-stone-500">Estimated amounts: {n.estimated.join(', ')}</p>}
      {r.source_nutrition?.calories ? (
        <p className="mt-1 text-xs text-stone-400">Cook Well lists {r.source_nutrition.calories} kcal.</p>
      ) : null}
    </div>
  )
}

function Stat({ label, value, strong }: { label: string; value: string | number; strong?: boolean }) {
  return (
    <div>
      <div className={strong ? 'text-2xl font-bold text-ember' : 'text-lg font-semibold'}>{value}</div>
      <div className="text-xs text-stone-500">{label}</div>
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

function IngredientRow({ ing, recipeId }: { ing: Ingredient; recipeId: number }) {
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
    <li className="px-3 py-2 text-sm">
      <div className="flex items-baseline gap-3">
        <div className="min-w-0 flex-1">
          <span className="font-medium">{ing.name}</span>
          {ing.note && <span className="text-stone-500">, {ing.note}</span>}
          {ing.label && <span className="ml-2 text-stone-500">{ing.label}</span>}
        </div>
        {editingGrams ? (
          <input
            autoFocus
            className={`${inputCls} w-20 py-0.5 text-right`}
            value={grams}
            inputMode="decimal"
            onChange={(e) => setGrams(e.target.value)}
            onBlur={saveGrams}
            onKeyDown={(e) => e.key === 'Enter' && saveGrams()}
          />
        ) : (
          <button
            onClick={() => setEditingGrams(true)}
            title={ing.grams_source ? SOURCE_NOTE[ing.grams_source] : 'not counted: click to add a weight'}
            className={`shrink-0 rounded px-1.5 tabular-nums hover:bg-stone-100 ${
              ing.grams == null ? 'text-amber-700' : ing.grams_source === 'estimate' ? 'text-stone-400 italic' : ''
            }`}
          >
            {ing.grams == null ? '+ g' : `${Math.round(ing.grams)} g`}
          </button>
        )}
        <span className="w-16 shrink-0 text-right tabular-nums text-stone-500">
          {ing.nutrition ? Math.round(ing.nutrition.kcal) : '—'}
        </span>
      </div>
      <button onClick={() => setPickingFood((v) => !v)} className="text-xs text-stone-400 hover:text-ember">
        {ing.food_name ?? 'no food linked'}
      </button>
      {pickingFood && (
        <FoodPicker
          initial={ing.name}
          onPick={(foodId) => {
            setPickingFood(false)
            patch.mutate({ id: ing.id, patch: { food_id: foodId } })
          }}
        />
      )}
    </li>
  )
}

function FoodPicker({ initial, onPick }: { initial: string; onPick: (id: number) => void }) {
  const [q, setQ] = useState(initial)
  const dq = useDebounced(q)
  const foods = useQuery({ queryKey: ['foods', dq], queryFn: () => api.foods(dq), enabled: dq.trim().length > 1 })
  return (
    <div className="mt-2 rounded-lg bg-stone-50 p-2 ring-1 ring-stone-200">
      <input autoFocus className={`${inputCls} w-full`} value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search foods…" />
      <ul className="mt-1 max-h-56 overflow-auto">
        {foods.data?.map((f) => (
          <li key={f.id}>
            <button onClick={() => onPick(f.id)} className="flex w-full justify-between gap-2 rounded px-2 py-1 text-left text-xs hover:bg-white">
              <span>{f.name}</span>
              <span className="shrink-0 text-stone-500">{Math.round(f.kcal)} kcal/100 g</span>
            </button>
          </li>
        ))}
        {foods.data?.length === 0 && <li className="px-2 py-1 text-xs text-stone-500">No match. Try fewer words.</li>}
      </ul>
    </div>
  )
}

function AddToPlan({ recipeId, defaultServings }: { recipeId: number; defaultServings: number }) {
  const qc = useQueryClient()
  const [day, setDay] = useState(today())
  const [servings, setServings] = useState(String(defaultServings))
  const add = useMutation({
    mutationFn: () => api.addEntry({ day: day || null, recipe_id: recipeId, servings: Number(servings) || 1 }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['plan'] }),
  })
  return (
    <div className="flex flex-wrap items-center gap-2 text-sm">
      <input type="date" className={inputCls} value={day} onChange={(e) => setDay(e.target.value)} />
      <label className="flex items-center gap-1">
        <input className={`${inputCls} w-16`} inputMode="decimal" value={servings} onChange={(e) => setServings(e.target.value)} />
        servings
      </label>
      <Button onClick={() => add.mutate()} disabled={add.isPending}>{day ? 'Add to plan' : 'Add to queue'}</Button>
      {add.isSuccess && <Link to="/planner" className="text-ember hover:underline">Added. Open planner →</Link>}
    </div>
  )
}
