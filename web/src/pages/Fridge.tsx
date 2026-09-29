import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type PlanEntry, type PrepStock } from '../api'
import { mealNow } from '../components/MealLog'
import { dayLabel, daysAgo, today } from '../dates'
import { Button, Empty, PageHeader, SectionTitle, Shimmer, inputCls } from '../components/ui'
import { kcal, num, plural, thumb } from '../format'
import { refreshPlan } from '../plan'

// Cooked food keeps about 3–4 days in the fridge.
const EAT_SOON_DAYS = 3

export default function Fridge() {
  const batches = useQuery({ queryKey: ['batches'], queryFn: api.batches })
  const cooked = batches.data?.filter((b) => b.day && b.day <= today()) ?? []
  const planned = batches.data?.filter((b) => !b.day || b.day > today()) ?? []
  const portions = cooked.reduce((s, b) => s + (b.portions_left ?? 0), 0)
  const stock = useQuery({ queryKey: ['prep-stock'], queryFn: api.prepStock })
  const preps = stock.data?.filter((p) => p.day && p.day <= today()) ?? []
  const prepsLater = stock.data?.filter((p) => !p.day || p.day > today()) ?? []

  return (
    <div className="rise">
      <PageHeader title="Fridge" subtitle={cooked.length || preps.length
        ? [cooked.length > 0 && `${plural(portions, 'portion')} ready to eat`, preps.length > 0 && plural(preps.length, 'prepped ingredient')].filter(Boolean).join(' · ')
        : 'Batch cooking and prep'} />
      {batches.isError && <p className="text-red-700">Couldn't load batches: {String(batches.error)}</p>}
      {batches.isPending && <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">{[0, 1, 2].map((i) => <Shimmer key={i} className="h-96 rounded-[28px]" />)}</div>}
      {batches.data && cooked.length === 0 && preps.length === 0 && (
        <Empty emoji="🥡" title="Nothing in the fridge"
          body="Plan a meal that cooks more than you eat (or use “Batch cook” on a planned meal). Once its day comes, the rest shows up here." />
      )}
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {cooked.map((b) => <BatchCard key={b.id} batch={b} />)}
      </div>

      <SectionTitle action={<AddPrep />}>🫙 Prepped</SectionTitle>
      {stock.data && preps.length === 0 && (
        <p className="rounded-3xl bg-paper p-5 text-stone-500">
          No prepped ingredients in the fridge. Plan a prep (cooked rice, pickled onions, a sauce) from its recipe page, or add what you already have.
        </p>
      )}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {preps.map((p) => <PrepCard key={p.entry_id} prep={p} />)}
      </div>
      {prepsLater.length > 0 && (
        <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {prepsLater.map((p) => <PrepCard key={p.entry_id} prep={p} later />)}
        </div>
      )}

      {planned.length > 0 && (
        <section>
          <SectionTitle>Coming up</SectionTitle>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {planned.map((b) => (
              <Link key={b.id} to={`/recipes/${b.recipe_id}`} className="press lift flex items-center gap-3 rounded-3xl bg-paper p-3">
                <img src={thumb(b.image_url, 140)} alt="" className="h-14 w-14 rounded-2xl bg-sand object-cover" />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold">{b.title}</p>
                  <p className="text-sm text-stone-500">{b.day ? `Cooking ${dayLabel(b.day).weekday} ${dayLabel(b.day).date}` : 'In the queue'}</p>
                </div>
                <div className="text-center">
                  <p className="font-display text-2xl font-bold text-amber-deep">{num(b.portions_left ?? 0)}</p>
                  <p className="text-[10px] text-stone-500">for later</p>
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}

function BatchCard({ batch: b }: { batch: PlanEntry }) {
  const qc = useQueryClient()
  // Show the change at once; the server's answer follows.
  const optimistic = (change: (e: PlanEntry) => PlanEntry) =>
    qc.setQueryData<PlanEntry[]>(['batches'], (list) => list?.map((e) => (e.id === b.id ? change(e) : e)))
  const eat = useMutation({
    mutationFn: () => api.addEntry({ day: today(), meal: mealNow(), leftover_of: b.id, servings: 1 }),
    onMutate: () => optimistic((e) => ({ ...e, portions_left: (e.portions_left ?? 0) - 1 })),
    onSettled: () => refreshPlan(qc),
  })
  const toss = useMutation({
    mutationFn: () => api.updateEntry(b.id, { discarded: b.discarded + (b.portions_left ?? 0) }),
    onMutate: () => optimistic((e) => ({ ...e, portions_left: 0 })),
    onSettled: () => refreshPlan(qc),
  })
  const age = daysAgo(b.day!)
  const old = age >= EAT_SOON_DAYS
  const left = b.portions_left ?? 0
  const made = b.cook_portions ?? 0

  return (
    <div className="lift overflow-hidden rounded-[28px] bg-paper">
      <Link to={`/recipes/${b.recipe_id}`} className="relative block h-48">
        {b.image_url && <img src={thumb(b.image_url, 900, 500)} alt="" className="h-full w-full object-cover" />}
        <div className="absolute inset-0 bg-gradient-to-b from-transparent from-40% to-black/75" />
        <span className={`absolute left-4 top-4 rounded-full px-2.5 py-0.5 text-xs font-semibold ${old ? 'bg-pink-deep text-white' : 'bg-white text-black'}`}>
          {old ? `⏰ Eat soon · ${age}d` : age === 0 ? 'Cooked today' : age === 1 ? 'Cooked yesterday' : `Cooked ${age} days ago`}
        </span>
        <h3 className="absolute inset-x-4 bottom-4 line-clamp-2 font-display text-2xl font-bold text-white">{b.title}</h3>
      </Link>
      <div className="p-5">
        <p className="flex items-baseline gap-2">
          <span className="font-display text-5xl font-extrabold">{num(left)}</span>
          <span className="text-stone-500">of {num(made)} left · {kcal(b.kcal_per_serving)} each</span>
        </p>
        {/* One dot per portion: filled while it's still in the fridge. */}
        <div className="mt-3 flex flex-wrap gap-1.5">
          {Array.from({ length: Math.min(24, Math.ceil(made)) }, (_, i) => (
            <span key={i} className={`h-3.5 w-3.5 rounded-full border-2 transition-colors ${i < left ? 'border-ember-bright bg-ember-bright' : 'border-stone-200'}`} />
          ))}
        </div>
        <div className="mt-4 grid grid-cols-2 gap-2">
          <Button variant="accent" onClick={() => eat.mutate()} disabled={left <= 0 || eat.isPending}>Ate one</Button>
          <Button variant="ghost" disabled={toss.isPending}
            onClick={() => confirm(`Mark the ${plural(left, 'portion')} of ${b.title} left in the fridge as thrown away?`) && toss.mutate()}>
            Toss the rest
          </Button>
        </div>
      </div>
    </div>
  )
}

/** A prepped ingredient in the fridge: grams left, and the planned meals that will use some. */
function PrepCard({ prep: p, later }: { prep: PrepStock; later?: boolean }) {
  const qc = useQueryClient()
  const discard = useMutation({
    mutationFn: (grams: number) => api.updateEntry(p.entry_id, { discarded: p.discarded + grams }),
    onMutate: (grams) => qc.setQueryData<PrepStock[]>(['prep-stock'], (l) => l?.map((x) => (x.entry_id === p.entry_id
      ? { ...x, grams_now: Math.max(0, x.grams_now - grams), grams_left: Math.max(0, x.grams_left - grams) } : x))),
    onSettled: () => refreshPlan(qc),
  })
  const age = p.day ? daysAgo(p.day) : 0
  const old = !later && age >= EAT_SOON_DAYS
  const share = p.made_grams ? Math.min(1, p.grams_now / p.made_grams) : 0
  const planned = p.made_grams ? Math.min(share, (p.grams_now - p.grams_left) / p.made_grams) : 0
  const upcoming = p.uses.filter((u) => !u.day || u.day >= today())
  return (
    <div className={`rounded-3xl bg-paper p-4 ${later ? 'opacity-80' : ''}`}>
      <div className="flex items-center gap-3">
        {p.image_url ? <img src={thumb(p.image_url, 120)} alt="" className="h-14 w-14 rounded-2xl bg-sand object-cover" />
          : <div className="grid h-14 w-14 place-items-center rounded-2xl bg-ember-soft text-2xl">🫙</div>}
        <div className="min-w-0 flex-1">
          <Link to={`/recipes/${p.recipe_id}`} className="line-clamp-1 font-display text-lg font-bold hover:text-ember">{p.title}</Link>
          <p className={`text-xs font-semibold ${old ? 'text-pink-deep' : 'text-stone-500'}`}>
            {later ? (p.day ? `Making ${dayLabel(p.day).weekday} ${dayLabel(p.day).date}` : 'In the queue')
              : old ? `⏰ Use soon · made ${age}d ago` : age === 0 ? 'Made today' : age === 1 ? 'Made yesterday' : `Made ${age} days ago`}
          </p>
        </div>
        <div className="text-right">
          <p className="font-display text-3xl font-extrabold tabular-nums">{Math.round(later ? p.made_grams : p.grams_now)}<span className="text-base font-bold text-stone-500"> g</span></p>
          <p className="text-[11px] text-stone-500">{later ? 'to make' : `in the fridge · of ${Math.round(p.made_grams)} g`}</p>
        </div>
      </div>
      {/* Green: spare; lighter: set aside for planned meals. */}
      <div className="mt-3 flex h-2 overflow-hidden rounded-full bg-sand">
        <div className="h-full bg-ember-bright transition-[width]" style={{ width: `${(share - planned) * 100}%` }} />
        <div className="h-full bg-ember-bright/35 transition-[width]" style={{ width: `${planned * 100}%` }} />
      </div>
      {upcoming.length > 0 && (
        <p className="mt-2 text-xs text-stone-500">
          Planned: {upcoming.map((u) => `${Math.round(u.grams)} g for ${u.title}${u.day ? ` (${u.day === today() ? 'today' : dayLabel(u.day).weekday})` : ''}`).join(', ')}
          {' · '}<span className="font-semibold text-ember">{Math.round(p.grams_left)} g spare</span>
        </p>
      )}
      {p.kcal_per_100g != null && <p className="mt-1 text-xs text-stone-400">{kcal(p.kcal_per_100g)} per 100 g</p>}
      {!later && (
        <div className="mt-3 flex gap-2">
          <Button variant="ghost" className="flex-1 px-3 py-2" disabled={discard.isPending} onClick={() => {
            const v = prompt(`How many grams of ${p.title} did you use (outside the plan)?`, '')
            if (v && Number(v) > 0) discard.mutate(Number(v))
          }}>Used some</Button>
          <Button variant="ghost" className="flex-1 px-3 py-2" disabled={discard.isPending}
            onClick={() => confirm(`Mark the ${Math.round(p.grams_now)} g of ${p.title} in the fridge as used up or thrown away?${upcoming.length ? ' Meals planned with it will buy its ingredients instead.' : ''}`) && discard.mutate(p.grams_now)}>
            Finished
          </Button>
        </div>
      )}
    </div>
  )
}

/** Put a prep you already have in the fridge (made today, by weight). */
function AddPrep() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const preps = useQuery({ queryKey: ['preps'], queryFn: api.preps, enabled: open })
  const [recipe, setRecipe] = useState<number | null>(null)
  const [grams, setGrams] = useState('')
  const add = useMutation({
    mutationFn: () => api.addEntry({ day: today(), recipe_id: recipe!, made_grams: Number(grams) || null }),
    onSuccess: () => { refreshPlan(qc); setOpen(false); setRecipe(null); setGrams('') },
  })
  return (
    <div className="relative">
      <Button variant="ghost" className="px-4 py-2" onClick={() => setOpen(!open)}>＋ Add</Button>
      {open && (
        <div className="rise absolute right-0 z-30 mt-2 w-72 rounded-2xl bg-paper p-3 shadow-2xl ring-1 ring-stone-200">
          <p className="px-1 pb-2 text-xs font-semibold uppercase tracking-wider text-stone-500">Already made some?</p>
          <div className="max-h-52 overflow-auto">
            {preps.data?.map((r) => (
              <button key={r.id} onClick={() => setRecipe(r.id)}
                className={`w-full rounded-xl px-2 py-2 text-left text-sm font-semibold ${recipe === r.id ? 'bg-ink text-cream' : 'hover:bg-sand'}`}>🫙 {r.title}</button>
            ))}
            {preps.data?.length === 0 && <p className="px-1 text-sm text-stone-500">No prepped ingredients yet: switch it on from a recipe's page.</p>}
          </div>
          {recipe != null && (
            <div className="mt-2 flex items-center gap-2">
              <input autoFocus value={grams} inputMode="decimal" placeholder="whole recipe" onChange={(e) => setGrams(e.target.value.replace(/[^\d.]/g, ''))}
                className={`${inputCls} w-full py-2`} />
              <span className="text-sm">g</span>
              <Button variant="accent" className="px-4 py-2" disabled={add.isPending} onClick={() => add.mutate()}>Add</Button>
            </div>
          )}
          {add.isError && <p className="mt-2 text-xs text-danger">{String(add.error)}</p>}
        </div>
      )}
    </div>
  )
}
