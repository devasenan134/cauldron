import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, type FoodDetail, type FoodIn, type FoodRow, type Unlinked } from '../api'
import { Button, Chip, Empty, PageHeader, Shimmer, inputCls } from '../components/ui'
import { num, plural } from '../format'
import { FoodPicker } from './RecipeDetail'

type Show = 'all' | 'yours' | 'unlinked'

/** Every food your recipes use, with its macros per 100 g; edit one and every recipe follows. */
export default function Ingredients() {
  const navigate = useNavigate()
  const library = useQuery({ queryKey: ['food-library'], queryFn: api.foodLibrary })
  const unlinked = useQuery({ queryKey: ['unlinked'], queryFn: api.unlinked })
  const [q, setQ] = useState('')
  const [show, setShow] = useState<Show>('all')
  const words = q.toLowerCase().split(/\s+/).filter(Boolean)
  const matches = (f: FoodRow) => words.every((w) => f.name.toLowerCase().includes(w) || f.brand.toLowerCase().includes(w) || f.names.some((n) => n.includes(w)))
  const rows = (library.data ?? []).filter((f) => (show !== 'yours' || f.edited || f.own) && matches(f))
  const yours = library.data?.filter((f) => f.edited || f.own).length ?? 0

  return (
    <div className="rise mx-auto max-w-4xl">
      <PageHeader title="Ingredients" subtitle={library.data ? `${library.data.length} foods · macros per 100 g` : 'Loading…'}
        actions={<Button onClick={() => navigate('/ingredients/new')}>＋ Add a food</Button>} />
      <p className="-mt-3 mb-5 max-w-2xl text-stone-500">
        Change a food's macros (say, the brand of curd you buy) and every recipe that uses it follows. Only you see your changes.
      </p>
      <input className={`${inputCls} w-full`} placeholder="Search ingredients" value={q} onChange={(e) => setQ(e.target.value)} />
      <div className="mt-3 flex flex-wrap gap-2">
        <Chip selected={show === 'all'} onClick={() => setShow('all')}>All</Chip>
        <Chip selected={show === 'yours'} onClick={() => setShow('yours')}>Edited by you{yours ? ` · ${yours}` : ''}</Chip>
        <Chip selected={show === 'unlinked'} onClick={() => setShow('unlinked')}>Not counted{unlinked.data?.length ? ` · ${unlinked.data.length}` : ''}</Chip>
      </div>

      {show === 'unlinked' ? (
        <UnlinkedList items={(unlinked.data ?? []).filter((u) => words.every((w) => u.name.toLowerCase().includes(w)))} loading={unlinked.isPending} />
      ) : (
        <div className="mt-4 overflow-hidden rounded-3xl bg-paper">
          {library.isPending && <div className="space-y-2 p-4">{[0, 1, 2, 3].map((i) => <Shimmer key={i} className="h-12 rounded-xl" />)}</div>}
          {library.data && rows.length === 0 && (
            show === 'yours' && !q
              ? <Empty emoji="🥕" title="Nothing edited yet" body="Open a food to set your own macros and notes, or add a food of your own from its label." />
              : <Empty emoji="🔍" title="No ingredient found" body="Try another word, or add it as your own food." />
          )}
          <ul className="divide-y divide-stone-100">
            {rows.slice(0, 300).map((f) => (
              <li key={f.id}>
                <Link to={`/ingredients/${f.id}`} className="flex items-center gap-3 px-4 py-3 hover:bg-sand">
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-semibold">
                      {f.name}
                      {f.brand && <span className="font-normal text-stone-500"> · {f.brand}</span>}
                      {(f.edited || f.own) && <span className="ml-2 rounded-full bg-ember-soft px-2 py-0.5 text-[11px] font-bold text-ember">{f.own ? 'your food' : 'edited'}</span>}
                    </p>
                    <p className="truncate text-xs text-stone-500">
                      {f.recipes ? `${plural(f.recipes, 'recipe')}` : 'not used yet'}
                      {f.names.length > 0 && ` · as ${f.names.slice(0, 3).join(', ')}`}
                      {f.notes && ` · 📝 ${f.notes}`}
                    </p>
                  </div>
                  <Macros f={f} />
                </Link>
              </li>
            ))}
          </ul>
          {rows.length > 300 && <p className="p-4 text-center text-sm text-stone-500">Showing 300 of {rows.length}: search to narrow it down.</p>}
        </div>
      )}
    </div>
  )
}

function Macros({ f }: { f: Pick<FoodRow, 'kcal' | 'protein' | 'carbs' | 'fat'> }) {
  return (
    <div className="shrink-0 text-right">
      <p className="font-display font-bold tabular-nums">{Math.round(f.kcal)} <span className="text-xs font-semibold text-stone-500">kcal</span></p>
      <p className="text-[11px] tabular-nums text-stone-500">P {num(f.protein)} · C {num(f.carbs)} · F {num(f.fat)}</p>
    </div>
  )
}

/** Ingredients that have no food, so they add nothing: pick one and every recipe that uses the name gets it. */
function UnlinkedList({ items, loading }: { items: Unlinked[]; loading: boolean }) {
  const qc = useQueryClient()
  const [open, setOpen] = useState<string | null>(null)
  const assign = useMutation({
    mutationFn: ({ food, name }: { food: number; name: string }) => api.assignFood(food, name),
    onSuccess: () => {
      setOpen(null)
      qc.invalidateQueries({ queryKey: ['unlinked'] })
      qc.invalidateQueries({ queryKey: ['food-library'] })
      qc.invalidateQueries({ queryKey: ['recipe'] })
      qc.invalidateQueries({ queryKey: ['recipes'] })
      qc.invalidateQueries({ queryKey: ['plan'] })
    },
  })
  if (loading) return <Shimmer className="mt-4 h-40 rounded-3xl" />
  if (items.length === 0) return <Empty emoji="✅" title="Everything is counted" body="Every ingredient in the recipes you can edit has a food (or is a prep)." />
  return (
    <div className="mt-4 rounded-3xl bg-paper">
      <p className="px-4 pt-4 text-sm text-stone-500">These have no food, so they add no calories. Pick one and it's used everywhere the ingredient appears.</p>
      <ul className="divide-y divide-stone-100">
        {items.map((u) => (
          <li key={u.name} className="px-4 py-3">
            <div className="flex items-center gap-3">
              <div className="min-w-0 flex-1">
                <p className="font-semibold">{u.name} <span className="font-normal text-stone-500">· {plural(u.count, 'time')}</span></p>
                <p className="truncate text-xs text-stone-500">
                  in {u.recipes.map((r, i) => <span key={r.id}>{i > 0 && ', '}<Link to={`/recipes/${r.id}`} className="hover:text-ember hover:underline">{r.title}</Link></span>)}
                </p>
              </div>
              <Button variant="soft" className="px-4 py-2" onClick={() => setOpen(open === u.name ? null : u.name)}>Pick a food</Button>
            </div>
            {open === u.name && <FoodPicker initial={u.name} onPick={(food) => assign.mutate({ food, name: u.name })} />}
          </li>
        ))}
      </ul>
      {assign.isError && <p className="p-4 text-sm text-danger">{String(assign.error)}</p>}
    </div>
  )
}

const FIELDS: [keyof FoodIn, string, string][] = [
  ['kcal', 'Calories', 'kcal'], ['protein', 'Protein', 'g'], ['carbs', 'Carbs', 'g'], ['fat', 'Fat', 'g'],
  ['fiber', 'Fiber', 'g'], ['sugar', 'Sugar', 'g'], ['sodium_mg', 'Sodium', 'mg'],
]
const OPTIONAL = new Set<keyof FoodIn>(['fiber', 'sugar', 'sodium_mg'])
type Form = Record<keyof FoodIn, string>

const toForm = (f?: FoodDetail): Form => ({
  name: f?.name ?? '', brand: f?.brand ?? '', notes: f?.notes ?? '',
  ...Object.fromEntries(FIELDS.map(([k]) => [k, f?.[k] == null ? '' : String(f[k])])),
} as Form)

/** One food: its macros per 100 g (yours if you've changed them), notes, and where it's used. */
export function FoodPage() {
  const params = useParams()
  const id = params.id && params.id !== 'new' ? Number(params.id) : null
  const food = useQuery({ queryKey: ['food', id], queryFn: () => api.food(id!), enabled: id != null })
  if (id != null && food.isPending) return <Shimmer className="mx-auto h-96 max-w-2xl rounded-3xl" />
  if (food.isError) return <p className="text-danger">Couldn't load the food: {String(food.error)}</p>
  return <FoodEditor key={id ?? 'new'} food={food.data} />
}

function FoodEditor({ food }: { food?: FoodDetail }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [f, setF] = useState<Form>(toForm(food))
  // Labels often give values per serving: scale them to 100 g on save.
  const [per, setPer] = useState('100')
  const [error, setError] = useState<string | null>(null)
  const scale = 100 / (Number(per) || 100)
  const body = (): FoodIn => ({
    name: f.name.trim(), brand: f.brand.trim(), notes: f.notes.trim(),
    ...Object.fromEntries(FIELDS.map(([k]) => {
      const v = f[k].trim() === '' ? (OPTIONAL.has(k) ? null : 0) : Math.round(Number(f[k]) * scale * 100) / 100
      return [k, v]
    })),
  } as FoodIn)
  const refresh = (id: number) => {
    qc.invalidateQueries({ queryKey: ['food', id] })
    qc.invalidateQueries({ queryKey: ['food-library'] })
    qc.invalidateQueries({ queryKey: ['foods'] })
    qc.invalidateQueries({ queryKey: ['recipe'] })
    qc.invalidateQueries({ queryKey: ['recipes'] })
    qc.invalidateQueries({ queryKey: ['plan'] })
    qc.invalidateQueries({ queryKey: ['catalog'] })
  }
  const save = useMutation({
    mutationFn: () => (food ? api.saveFood(food.id, body()) : api.addFood(body())),
    onSuccess: (d) => {
      qc.setQueryData(['food', d.id], d)
      refresh(d.id)
      setF(toForm(d))
      setPer('100')
      if (!food) navigate(`/ingredients/${d.id}`, { replace: true })
    },
    onError: (e) => setError(String(e)),
  })
  const reset = useMutation({
    mutationFn: () => api.resetFood(food!.id),
    onSuccess: () => {
      refresh(food!.id)
      if (food!.own) navigate('/ingredients', { replace: true })
      else api.food(food!.id).then((d) => { qc.setQueryData(['food', d.id], d); setF(toForm(d)) })
    },
  })
  const dirty = JSON.stringify(toForm(food)) !== JSON.stringify(f) || per !== '100'

  return (
    <div className="rise mx-auto max-w-2xl">
      <div className="mb-4 flex items-center gap-3">
        <button onClick={() => navigate(-1)} className="press grid h-11 w-11 place-items-center rounded-full bg-paper text-lg ring-1 ring-stone-200" aria-label="Back">←</button>
        <h1 className="flex-1 truncate font-display text-3xl font-extrabold">{food ? food.name : 'New food'}</h1>
        <Button variant="accent" onClick={() => { setError(null); if (!f.name.trim()) setError('Give it a name'); else save.mutate() }} disabled={save.isPending || (food && !dirty)}>
          {save.isPending ? 'Saving…' : 'Save'}
        </Button>
      </div>
      {food && (
        <p className="mb-4 text-sm text-stone-500">
          {food.own ? 'A food you added.' : food.edited ? 'Your version: it replaces the standard values in all your recipes.' : `Standard values (${food.source.startsWith('usda') ? 'USDA' : 'built in'}). Saving makes your own version, used in all your recipes.`}
          {food.recipes > 0 && ` Used in ${plural(food.recipes, 'recipe')}.`}
        </p>
      )}
      {!food && <p className="mb-4 text-sm text-stone-500">A product off its label, or anything the search doesn't have. Pick it for an ingredient from the recipe page (⇄).</p>}
      {error && <p className="mb-4 rounded-2xl bg-pink-soft p-3 text-sm font-semibold text-pink-deep">{error}</p>}

      <div className="rounded-3xl bg-paper p-5">
        <Field label="Name"><input className={`${inputCls} w-full`} value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} placeholder="e.g. Curd" /></Field>
        <Field label="Brand"><input className={`${inputCls} w-full`} value={f.brand} onChange={(e) => setF({ ...f, brand: e.target.value })} placeholder="optional, e.g. Amul" /></Field>
        <Field label="Notes">
          <textarea className="w-full rounded-2xl bg-cream px-4 py-3 outline-none ring-1 ring-stone-200 focus:ring-2 focus:ring-ember-bright/50" rows={2}
            value={f.notes} onChange={(e) => setF({ ...f, notes: e.target.value })} placeholder="Where you buy it, which pack…" />
        </Field>
      </div>

      <div className="mt-4 rounded-3xl bg-paper p-5">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <h2 className="flex-1 font-display text-xl font-bold">Nutrition</h2>
          <span className="text-sm text-stone-500">per</span>
          <input value={per} inputMode="decimal" onChange={(e) => setPer(e.target.value.replace(/[^\d.]/g, ''))}
            className="w-20 rounded-xl bg-cream px-3 py-1.5 text-right font-semibold outline-none ring-1 ring-stone-200 focus:ring-2 focus:ring-ember-bright/50" />
          <span className="text-sm text-stone-500">g</span>
        </div>
        {per !== '100' && Number(per) > 0 && <p className="mb-3 rounded-xl bg-ember-soft p-2 text-xs font-semibold text-ember">Type the label's numbers for {per} g; they're saved per 100 g (× {num(scale)}).</p>}
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {FIELDS.map(([k, label, unit]) => (
            <label key={k} className="block rounded-2xl bg-cream p-3">
              <span className="block text-xs font-semibold uppercase tracking-wider text-stone-500">{label}</span>
              <span className="mt-1 flex items-baseline gap-1">
                <input value={f[k]} inputMode="decimal" placeholder={OPTIONAL.has(k) ? '—' : '0'}
                  onChange={(e) => setF({ ...f, [k]: e.target.value.replace(/[^\d.]/g, '') })}
                  className="w-full min-w-0 bg-transparent font-display text-2xl font-bold outline-none" />
                <span className="text-sm text-stone-500">{unit}</span>
              </span>
              {food?.default && food.default[k as keyof typeof food.default] != null && String(food.default[k as keyof typeof food.default]) !== f[k] && (
                <span className="block text-[11px] text-stone-400">standard {num(Number(food.default[k as keyof typeof food.default]))}</span>
              )}
            </label>
          ))}
        </div>
        <p className="mt-3 text-xs text-stone-500">
          From the macros: {Math.round(4 * (Number(f.protein) + Number(f.carbs)) * scale + 9 * Number(f.fat) * scale)} kcal per 100 g (protein and carbs 4 kcal/g, fat 9).
        </p>
      </div>

      {food && food.used_in.length > 0 && (
        <div className="mt-4 rounded-3xl bg-paper p-5">
          <h2 className="font-display text-xl font-bold">Used in</h2>
          {food.names.length > 0 && <p className="text-sm text-stone-500">as {food.names.join(', ')}</p>}
          <div className="mt-3 flex flex-wrap gap-2">
            {food.used_in.slice(0, 60).map((r) => <Link key={r.id} to={`/recipes/${r.id}`} className="rounded-full bg-sand px-3 py-1.5 text-sm font-semibold hover:bg-stone-200">{r.title}</Link>)}
            {food.used_in.length > 60 && <span className="px-2 py-1.5 text-sm text-stone-500">and {food.used_in.length - 60} more</span>}
          </div>
        </div>
      )}
      {food?.portions && food.portions.length > 0 && (
        <p className="mt-4 px-2 text-xs text-stone-400">Weights used for counts: {food.portions.slice(0, 8).map((p) => `${p.unit} = ${num(p.grams)} g`).join(' · ')}</p>
      )}
      {food && (food.edited || food.own) && (
        <button className="mt-6 font-semibold text-danger hover:underline" disabled={reset.isPending}
          onClick={() => confirm(food.own ? `Delete ${food.name}? Ingredients using it won't be counted.` : 'Go back to the standard values in all your recipes?') && reset.mutate()}>
          {food.own ? '🗑 Delete this food' : '↺ Back to the standard values'}
        </button>
      )}
      <div className="h-16" />
    </div>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="mb-3 block last:mb-0">
      <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-stone-500">{label}</span>
      {children}
    </label>
  )
}
