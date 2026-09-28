import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api, type PlanEntry } from '../api'
import { dayLabel, daysAgo, today } from '../dates'
import { Button, kcal, thumb } from '../components/ui'
import { refreshPlan } from '../plan'

// Cooked food keeps about 3–4 days in the fridge.
const EAT_SOON_DAYS = 3

export default function Fridge() {
  const batches = useQuery({ queryKey: ['batches'], queryFn: api.batches })
  const cooked = batches.data?.filter((b) => b.day && b.day <= today()) ?? []
  const planned = batches.data?.filter((b) => !b.day || b.day > today()) ?? []
  const portions = cooked.reduce((s, b) => s + (b.portions_left ?? 0), 0)

  return (
    <div className="space-y-8">
      <section>
        <h1 className="text-lg font-semibold">
          In the fridge
          {cooked.length > 0 && <span className="ml-3 text-sm font-normal text-stone-500">{portions} {portions === 1 ? 'portion' : 'portions'}</span>}
        </h1>
        <p className="mb-4 text-sm text-stone-500">
          Batches you've cooked with portions left over (after the leftovers you've already planned).
        </p>
        {batches.isError && <p className="text-red-700">Couldn't load batches: {String(batches.error)}</p>}
        {batches.data && cooked.length === 0 && (
          <p className="rounded-xl bg-white p-4 text-sm text-stone-500 ring-1 ring-stone-200">
            Nothing yet. Plan a meal with “cook” more than “eat” (or “+ batch cook” on the planner), and once its day
            comes the rest shows up here.
          </p>
        )}
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {cooked.map((b) => <BatchCard key={b.id} batch={b} />)}
        </div>
      </section>

      {planned.length > 0 && (
        <section>
          <h2 className="mb-1 text-lg font-semibold">Coming up</h2>
          <p className="mb-4 text-sm text-stone-500">Batches on the plan that haven't been cooked yet.</p>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {planned.map((b) => <BatchCard key={b.id} batch={b} upcoming />)}
          </div>
        </section>
      )}
    </div>
  )
}

function BatchCard({ batch, upcoming }: { batch: PlanEntry; upcoming?: boolean }) {
  const qc = useQueryClient()
  const refresh = () => refreshPlan(qc)
  const eat = useMutation({
    mutationFn: () => api.addEntry({ day: today(), leftover_of: batch.id, servings: 1 }),
    onSettled: refresh,
  })
  const toss = useMutation({
    mutationFn: () => api.updateEntry(batch.id, { discarded: batch.discarded + (batch.portions_left ?? 0) }),
    onSettled: refresh,
  })
  const age = batch.day ? daysAgo(batch.day) : null
  const old = !upcoming && age != null && age >= EAT_SOON_DAYS

  return (
    <div className={`flex gap-3 rounded-xl bg-white p-3 ring-1 ${old ? 'ring-amber-300' : 'ring-stone-200'}`}>
      {batch.image_url && <img src={thumb(batch.image_url, 160)} alt="" className="h-20 w-20 shrink-0 rounded-lg object-cover" />}
      <div className="flex min-w-0 flex-1 flex-col">
        <Link to={`/recipes/${batch.recipe_id}`} className="line-clamp-2 text-sm font-semibold leading-tight hover:text-ember">
          {batch.title}
        </Link>
        <div className="mt-0.5 text-xs text-stone-500">
          {upcoming
            ? batch.day ? `cooking ${dayLabel(batch.day).weekday} ${dayLabel(batch.day).date}` : 'in the queue'
            : age === 0 ? 'cooked today' : age === 1 ? 'cooked yesterday' : `cooked ${age} days ago`}
          {old && <span className="ml-1 font-medium text-amber-700">· eat soon</span>}
        </div>
        <div className="mt-1 flex items-baseline gap-2">
          <span className="text-2xl font-bold tabular-nums">{batch.portions_left}</span>
          <span className="text-xs text-stone-500">
            of {batch.cook_portions} left · {kcal(batch.kcal_per_serving)} each
          </span>
        </div>
        {!upcoming && (
          <div className="mt-2 flex gap-2">
            <Button onClick={() => eat.mutate()} disabled={eat.isPending}>Ate 1 today</Button>
            <Button variant="ghost" onClick={() => toss.mutate()} disabled={toss.isPending}>Toss the rest</Button>
          </div>
        )}
      </div>
    </div>
  )
}
