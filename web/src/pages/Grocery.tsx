import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, type GroceryItem } from '../api'
import { Button, Empty, PageHeader } from '../components/ui'
import { aisleEmoji, aisleRank, parseQuickAdd } from '../grocery'

export default function Grocery() {
  const qc = useQueryClient()
  const items = useQuery({ queryKey: ['grocery'], queryFn: api.grocery })
  const refresh = () => qc.invalidateQueries({ queryKey: ['grocery'] })
  const edit = (change: (list: GroceryItem[]) => GroceryItem[]) => qc.setQueryData<GroceryItem[]>(['grocery'], (old) => old && change(old))

  const toggle = useMutation({
    mutationFn: (item: GroceryItem) => api.updateGrocery(item.id, { checked: !item.checked }),
    onMutate: (item) => edit((l) => l.map((i) => (i.id === item.id ? { ...i, checked: !i.checked } : i))),
    onSettled: refresh,
  })
  const remove = useMutation({
    mutationFn: (id: number) => api.deleteGrocery(id),
    onMutate: (id) => edit((l) => l.filter((i) => i.id !== id)),
    onSettled: refresh,
  })
  const clearChecked = useMutation({
    mutationFn: () => api.clearGrocery(true),
    onMutate: () => edit((l) => l.filter((i) => !i.checked)),
    onSettled: refresh,
  })

  const [text, setText] = useState('')
  const add = useMutation({ mutationFn: (item: { name: string; amount: string }) => api.addGrocery(item), onSettled: refresh })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!text.trim()) return
    add.mutate(parseQuickAdd(text))
    setText('')
  }

  const list = items.data ?? []
  const toBuy = list.filter((i) => !i.checked)
  const done = list.filter((i) => i.checked)
  const aisles = new Map<string, GroceryItem[]>()
  for (const i of [...toBuy].sort((a, b) => aisleRank(a.aisle) - aisleRank(b.aisle) || a.aisle.localeCompare(b.aisle))) {
    aisles.set(i.aisle, [...(aisles.get(i.aisle) ?? []), i])
  }
  const progress = list.length ? (done.length / list.length) * 100 : 0

  return (
    <div className="rise mx-auto max-w-2xl">
      <PageHeader title="Grocery" subtitle={list.length ? `${done.length} of ${list.length} in the cart` : 'Your list'} />
      {list.length > 0 && (
        <div className="mb-5 h-2.5 overflow-hidden rounded-full bg-stone-200">
          <div className="h-full rounded-full bg-ember-bright transition-[width] duration-500" style={{ width: `${progress}%` }} />
        </div>
      )}

      <form onSubmit={submit} className="mb-4 flex items-center gap-2 rounded-full bg-paper py-1.5 pl-5 pr-1.5 shadow-[0_6px_20px_rgba(0,0,0,0.06)] ring-1 ring-stone-200 focus-within:ring-2 focus-within:ring-ember-bright/50">
        <input className="min-w-0 flex-1 bg-transparent py-2 text-base outline-none placeholder:text-stone-400"
          placeholder="Add an item, e.g. “Paneer 200 g”" value={text} onChange={(e) => setText(e.target.value)} />
        <button type="submit" disabled={!text.trim()} aria-label="Add"
          className="press grid h-11 w-11 place-items-center rounded-full bg-ink text-2xl font-bold text-cream disabled:bg-stone-200 disabled:text-stone-400">+</button>
      </form>

      {items.isError && <p className="text-red-700">Couldn't load the list: {String(items.error)}</p>}
      {items.isSuccess && list.length === 0 && (
        <Empty emoji="🛒" title="Your list is empty" body="Plan some meals, then press “Make grocery list” on the planner."
          action={<Link to="/planner"><Button variant="soft">Open the plan</Button></Link>} />
      )}

      {[...aisles].map(([aisle, group]) => (
        <section key={aisle} className="mb-5">
          <ListHeader title={`${aisleEmoji(aisle)}  ${aisle}`} count={group.length} />
          <ul>
            {group.map((i) => <Row key={i.id} item={i} onToggle={() => toggle.mutate(i)} onRemove={() => remove.mutate(i.id)} />)}
          </ul>
        </section>
      ))}

      {done.length > 0 && (
        <section className="mt-8">
          <ListHeader title="In the cart" count={done.length}
            action={<button className="font-semibold hover:underline" onClick={() => clearChecked.mutate()}>Clear</button>} />
          <ul>
            {done.map((i) => <Row key={i.id} item={i} onToggle={() => toggle.mutate(i)} onRemove={() => remove.mutate(i.id)} />)}
          </ul>
        </section>
      )}
    </div>
  )
}

/** "Dairy & Eggs · 3" with a hairline running to the edge. */
function ListHeader({ title, count, action }: { title: string; count: number; action?: React.ReactNode }) {
  return (
    <div className="mb-1 mt-4 flex items-center gap-3">
      <span className="whitespace-pre font-semibold text-stone-500">{title} · {count}</span>
      <span className="h-px flex-1 bg-stone-200" />
      {action}
    </div>
  )
}

function Row({ item, onToggle, onRemove }: { item: GroceryItem; onToggle: () => void; onRemove: () => void }) {
  return (
    <li className="rise group flex items-center gap-3 py-2.5 pl-1">
      <button onClick={onToggle} className="min-w-0 flex-1 text-left">
        <p className={`truncate text-[17px] ${item.checked ? 'text-stone-400 line-through' : ''}`}>
          {item.name}
          {item.amount && <span className="ml-2 text-base text-stone-500">{item.amount}</span>}
        </p>
        {item.sources.length > 0 && !item.checked && <p className="truncate text-xs text-stone-400">{item.sources.join(' · ')}</p>}
      </button>
      <button onClick={onRemove} aria-label={`Remove ${item.name}`} className="rounded-full px-2 text-stone-300 hover:text-danger md:opacity-0 md:group-hover:opacity-100">✕</button>
      <button role="checkbox" aria-checked={item.checked} aria-label={item.name} onClick={onToggle}
        className={`press grid h-7 w-7 shrink-0 place-items-center rounded-full border-[1.5px] text-sm font-bold transition-colors ${
          item.checked ? 'border-ink bg-ink text-cream' : 'border-stone-400 hover:border-ink'}`}>
        {item.checked && '✓'}
      </button>
    </li>
  )
}
