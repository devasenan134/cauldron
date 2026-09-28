import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Me, type PlanEntry } from '../api'
import { ME, useMe } from '../auth'
import { addDays, daysAgo, dayLabel, greeting, today, weekStart, weekdayLong } from '../dates'
import { Button, SectionTitle, Shimmer } from '../components/ui'
import { kcal, num, plural, thumb } from '../format'

export default function Home() {
  const me = useMe().data
  const week = weekStart(today())
  const tomorrow = addDays(today(), 1)
  const plan = useQuery({ queryKey: ['plan', week], queryFn: () => api.plan(week) })
  const nextWeek = weekStart(tomorrow)
  const nextPlan = useQuery({ queryKey: ['plan', nextWeek], queryFn: () => api.plan(nextWeek), enabled: nextWeek !== week })
  const batches = useQuery({ queryKey: ['batches'], queryFn: api.batches })
  const grocery = useQuery({ queryKey: ['grocery'], queryFn: api.grocery })
  const [editingGoal, setEditingGoal] = useState(false)

  const todays = (plan.data?.days[today()] ?? []).filter((e) => e.recipe_id != null || e.leftover_of != null)
  const tomorrows = ((nextWeek === week ? plan.data : nextPlan.data)?.days[tomorrow] ?? []).filter((e) => e.recipe_id != null || e.leftover_of != null)
  const eaten = todays.reduce((s, e) => s + (e.kcal ?? 0), 0)
  const goal = me?.kcal_goal ?? 2200
  const inFridge = batches.data?.filter((b) => b.day && b.day <= today()) ?? []
  const toBuy = grocery.data?.filter((i) => !i.checked).length ?? 0
  const weekEntries = plan.data ? Object.values(plan.data.days).flat() : []
  const weekKcal = weekEntries.reduce((s, e) => s + (e.kcal ?? 0), 0)
  const firstName = me?.name?.split(' ')[0]

  return (
    <div className="rise">
      <div className="mb-6 flex items-end gap-3">
        <div className="flex-1">
          <p className="text-stone-500">{weekdayLong(today())}, {dayLabel(today()).date}</p>
          <h1 className="font-display text-4xl font-extrabold tracking-tight sm:text-5xl">{greeting()}{firstName ? `, ${firstName}` : ''}</h1>
        </div>
      </div>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <div>
          {plan.isPending ? <Shimmer className="h-80 rounded-[28px]" />
            : todays.length ? <Hero entry={todays[0]} label="Today" more={todays.length - 1} />
            : tomorrows.length ? <Hero entry={tomorrows[0]} label="Tomorrow" more={tomorrows.length - 1} />
            : <EmptyHero />}
        </div>
        <div className="flex flex-col gap-5">
          <CaloriesCard eaten={eaten} goal={goal} onEdit={() => setEditingGoal(true)} />
          {/* The grocery list lives here now (Profile took its place in the menu). */}
          <Link to="/grocery" className="press flex items-center gap-4 rounded-3xl bg-ink px-6 py-5 text-cream">
            <span className="text-2xl">🧺</span>
            <span className="flex-1">
              <span className="block font-display text-lg font-bold">Grocery list</span>
              <span className="text-sm opacity-70">{grocery.data?.length ? (toBuy ? `${plural(toBuy, 'item')} to buy` : 'All bought') : 'Empty: build it from your plan'}</span>
            </span>
            <span className="text-xl">→</span>
          </Link>
          <div className="grid grid-cols-2 gap-4">
            <Tile to="/fridge" emoji="🥡" title={plural(inFridge.reduce((s, b) => s + (b.portions_left ?? 0), 0), 'portion')} sub="in the fridge" />
            <Tile to="/planner" emoji="📅" title={plural(weekEntries.length, 'meal')} sub={`${kcal(weekKcal)} this week`} />
          </div>
        </div>
      </div>

      {editingGoal && <GoalDialog goal={goal} onClose={() => setEditingGoal(false)} />}

      <div className="grid gap-x-8 lg:grid-cols-2">
        {todays.length > 1 && (
          <section>
            <SectionTitle>Today's meals</SectionTitle>
            <div className="rounded-3xl bg-paper p-2">{todays.map((e) => <MealRow key={e.id} e={e} />)}</div>
          </section>
        )}
        {inFridge.length > 0 && (
          <section>
            <SectionTitle action={<Link to="/fridge" className="font-semibold text-ember hover:underline">See all</Link>}>In the fridge</SectionTitle>
            <div className="space-y-2">{inFridge.slice(0, 3).map((b) => <FridgeAlert key={b.id} b={b} />)}</div>
          </section>
        )}
        {todays.length > 0 && tomorrows.length > 0 && (
          <section>
            <SectionTitle>Tomorrow</SectionTitle>
            <div className="rounded-3xl bg-paper p-2">{tomorrows.map((e) => <MealRow key={e.id} e={e} />)}</div>
          </section>
        )}
      </div>
    </div>
  )
}

function Hero({ entry: e, label, more }: { entry: PlanEntry; label: string; more: number }) {
  const body = (
    <div className="lift group relative h-80 overflow-hidden rounded-[28px] bg-sand sm:h-96">
      {e.image_url && <img src={thumb(e.image_url, 1200, 800)} alt="" className="absolute inset-0 h-full w-full object-cover transition duration-500 group-hover:scale-105" />}
      <div className="absolute inset-0 bg-gradient-to-b from-transparent from-35% to-black/85" />
      <div className="absolute left-4 top-4 flex gap-2">
        <span className="rounded-full bg-black/55 px-2.5 py-0.5 text-xs font-semibold text-white">{label.toUpperCase()}</span>
        {e.leftover_of && <span className="rounded-full bg-white px-2.5 py-0.5 text-xs font-semibold text-black">LEFTOVERS</span>}
        {e.cook_portions != null && <span className="rounded-full bg-white px-2.5 py-0.5 text-xs font-semibold text-black">BATCH · {num(e.cook_portions)}</span>}
      </div>
      <div className="absolute inset-x-0 bottom-0 p-6">
        <h2 className="font-display text-3xl font-extrabold text-white sm:text-4xl">{e.title}</h2>
        <p className="mt-1 font-semibold text-white/85">
          {kcal(e.kcal)}{e.servings !== 1 && ` · ${plural(e.servings, 'serving')}`}{more > 0 && ` · +${more} more`}
        </p>
      </div>
    </div>
  )
  return e.recipe_id ? <Link to={`/recipes/${e.recipe_id}`}>{body}</Link> : body
}

function EmptyHero() {
  return (
    <div className="flex h-80 flex-col justify-end rounded-[28px] bg-ink p-7 sm:h-96">
      <div className="text-5xl">🍲</div>
      <h2 className="mt-3 font-display text-3xl font-bold text-cream">Nothing planned today</h2>
      <p className="mt-1 text-cream/70">Pick something tasty and put it on the plan.</p>
      <Link to="/recipes" className="mt-5"><Button variant="accent">Find a recipe</Button></Link>
    </div>
  )
}

function CaloriesCard({ eaten, goal, onEdit }: { eaten: number; goal: number; onEdit: () => void }) {
  const over = eaten > goal
  const left = goal - eaten
  return (
    <button onClick={onEdit} className="press rounded-3xl bg-paper p-6 text-left" title="Change your daily goal">
      <p className="text-sm text-stone-500">Today's calories</p>
      <p className="mt-1 flex items-baseline gap-2">
        <span className={`font-display text-5xl font-extrabold ${over ? 'text-red-700' : ''}`}>{Math.round(eaten).toLocaleString()}</span>
        <span className="text-stone-500">/ {goal.toLocaleString()} kcal</span>
      </p>
      <div className="mt-4 h-3 overflow-hidden rounded-full bg-stone-200">
        <div className={`h-full rounded-full transition-[width] duration-700 ${over ? 'bg-danger' : 'bg-ember-bright'}`}
          style={{ width: `${Math.min(100, (eaten / goal) * 100)}%` }} />
      </div>
      <p className="mt-2 text-xs text-stone-500">
        {left >= 0 ? `${Math.round(left).toLocaleString()} kcal left in your plan` : `${Math.round(-left).toLocaleString()} kcal over your goal`}
      </p>
    </button>
  )
}

function Tile({ to, emoji, title, sub }: { to: string; emoji: string; title: string; sub: string }) {
  return (
    <Link to={to} className="press lift rounded-3xl bg-paper p-5">
      <div className="text-3xl">{emoji}</div>
      <p className="mt-3 font-display text-lg font-bold">{title}</p>
      <p className="truncate text-xs text-stone-500">{sub}</p>
    </Link>
  )
}

function MealRow({ e }: { e: PlanEntry }) {
  const inner = (
    <div className="flex items-center gap-3 rounded-2xl p-2 hover:bg-sand">
      <img src={thumb(e.image_url, 120)} alt="" className="h-12 w-12 rounded-xl bg-sand object-cover" />
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold">{e.title}</p>
        <p className="text-xs text-stone-500">{e.leftover_of ? 'Leftovers' : e.cook_portions != null ? `Batch · cook ${num(e.cook_portions)}` : plural(e.servings, 'serving')}</p>
      </div>
      <span className="font-display font-bold text-ember">{e.kcal != null ? Math.round(e.kcal) : ''}</span>
    </div>
  )
  return e.recipe_id ? <Link to={`/recipes/${e.recipe_id}`}>{inner}</Link> : inner
}

function FridgeAlert({ b }: { b: PlanEntry }) {
  const age = daysAgo(b.day!)
  const soon = age >= 3
  return (
    <Link to="/fridge" className={`press flex items-center gap-3 rounded-3xl p-3 ${soon ? 'bg-pink-soft' : 'bg-paper'}`}>
      <img src={thumb(b.image_url, 120)} alt="" className="h-12 w-12 rounded-xl bg-sand object-cover" />
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold">{b.title}</p>
        <p className={`text-xs ${soon ? 'font-semibold text-pink-deep' : 'text-stone-500'}`}>
          {soon ? `Cooked ${age} days ago · eat soon` : age === 0 ? 'Cooked today' : `Cooked ${plural(age, 'day')} ago`}
        </p>
      </div>
      <div className="text-center">
        <p className="font-display text-2xl font-bold">{num(b.portions_left ?? 0)}</p>
        <p className="text-[10px] text-stone-500">left</p>
      </div>
    </Link>
  )
}

export function GoalDialog({ goal, onClose }: { goal: number; onClose: () => void }) {
  const qc = useQueryClient()
  const [text, setText] = useState(String(goal))
  const save = useMutation({
    mutationFn: (n: number) => api.setKcalGoal(n),
    onSuccess: (me: Me) => { qc.setQueryData(ME, me); onClose() },
  })
  const n = Number(text)
  const valid = Number.isInteger(n) && n >= 500 && n <= 10000
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" onClick={onClose}>
      <div className="rise w-full max-w-sm rounded-3xl bg-cream p-6 shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <h3 className="font-display text-2xl font-bold">Daily calorie goal</h3>
        <p className="mt-1 text-sm text-stone-500">Shown on Home, here and in the app.</p>
        <div className="mt-4 flex items-center gap-2">
          <input autoFocus inputMode="numeric" value={text} onChange={(e) => setText(e.target.value.replace(/\D/g, '').slice(0, 5))}
            onKeyDown={(e) => e.key === 'Enter' && valid && save.mutate(n)}
            className="w-full rounded-2xl bg-paper px-4 py-3 font-display text-2xl font-bold outline-none ring-1 ring-stone-200 focus:ring-2 focus:ring-ember-bright/50" />
          <span className="text-stone-500">kcal</span>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button variant="accent" disabled={!valid || save.isPending} onClick={() => save.mutate(n)}>Save</Button>
        </div>
      </div>
    </div>
  )
}
