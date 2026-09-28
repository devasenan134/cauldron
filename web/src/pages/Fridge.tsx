import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api, type PlanEntry } from '../api'
import { dayLabel, daysAgo, today } from '../dates'
import { Button, Empty, PageHeader, SectionTitle, Shimmer } from '../components/ui'
import { kcal, num, plural, thumb } from '../format'
import { refreshPlan } from '../plan'

// Cooked food keeps about 3–4 days in the fridge.
const EAT_SOON_DAYS = 3

export default function Fridge() {
  const batches = useQuery({ queryKey: ['batches'], queryFn: api.batches })
  const cooked = batches.data?.filter((b) => b.day && b.day <= today()) ?? []
  const planned = batches.data?.filter((b) => !b.day || b.day > today()) ?? []
  const portions = cooked.reduce((s, b) => s + (b.portions_left ?? 0), 0)

  return (
    <div className="rise">
      <PageHeader title="Fridge" subtitle={cooked.length ? `${plural(portions, 'portion')} ready to eat` : 'Batch cooking'} />
      {batches.isError && <p className="text-red-700">Couldn't load batches: {String(batches.error)}</p>}
      {batches.isPending && <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">{[0, 1, 2].map((i) => <Shimmer key={i} className="h-96 rounded-[28px]" />)}</div>}
      {batches.data && cooked.length === 0 && (
        <Empty emoji="🥡" title="Nothing in the fridge"
          body="Plan a meal that cooks more than you eat (or use “Batch cook” on a planned meal). Once its day comes, the rest shows up here." />
      )}
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {cooked.map((b) => <BatchCard key={b.id} batch={b} />)}
      </div>

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
    mutationFn: () => api.addEntry({ day: today(), leftover_of: b.id, servings: 1 }),
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
