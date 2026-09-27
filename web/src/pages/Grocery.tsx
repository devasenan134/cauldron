import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, type GroceryItem } from '../api'
import { Button, inputCls } from '../components/ui'

export default function Grocery() {
  const qc = useQueryClient()
  const items = useQuery({ queryKey: ['grocery'], queryFn: api.grocery })
  const refresh = () => qc.invalidateQueries({ queryKey: ['grocery'] })

  const toggle = useMutation({
    mutationFn: (item: GroceryItem) => api.updateGrocery(item.id, { checked: !item.checked }),
    onMutate: (item) =>
      qc.setQueryData<GroceryItem[]>(['grocery'], (old) =>
        old?.map((i) => (i.id === item.id ? { ...i, checked: !i.checked } : i)),
      ),
    onSettled: refresh,
  })
  const remove = useMutation({ mutationFn: (id: number) => api.deleteGrocery(id), onSettled: refresh })
  const clearChecked = useMutation({ mutationFn: () => api.clearGrocery(true), onSettled: refresh })

  const [name, setName] = useState('')
  const [amount, setAmount] = useState('')
  const add = useMutation({
    mutationFn: () => api.addGrocery({ name, amount }),
    onSuccess: () => {
      setName('')
      setAmount('')
    },
    onSettled: refresh,
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (name.trim()) add.mutate()
  }

  const list = items.data ?? []
  const toBuy = list.filter((i) => !i.checked)
  const done = list.filter((i) => i.checked)
  const aisles = new Map<string, GroceryItem[]>()
  for (const i of toBuy) aisles.set(i.aisle, [...(aisles.get(i.aisle) ?? []), i])

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-4 flex items-baseline justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Grocery list</h1>
        <span className="text-sm text-stone-500">{toBuy.length} to buy</span>
      </div>

      <form onSubmit={submit} className="mb-6 flex gap-2">
        <input className={`${inputCls} flex-1`} placeholder="Add an item…" value={name} onChange={(e) => setName(e.target.value)} />
        <input className={`${inputCls} w-28`} placeholder="Amount" value={amount} onChange={(e) => setAmount(e.target.value)} />
        <Button type="submit" disabled={!name.trim()}>Add</Button>
      </form>

      {items.isError && <p className="text-red-700">Couldn't load the list: {String(items.error)}</p>}
      {items.isSuccess && list.length === 0 && (
        <p className="text-stone-500">
          Empty. Plan some meals, then press <Link to="/planner" className="text-ember hover:underline">Make grocery list</Link>.
        </p>
      )}

      {[...aisles].map(([aisle, group]) => (
        <section key={aisle} className="mb-5">
          <h2 className="mb-1 text-xs font-semibold uppercase tracking-wide text-stone-500">{aisle}</h2>
          <ul className="divide-y divide-stone-200 rounded-xl bg-white ring-1 ring-stone-200">
            {group.map((i) => <Row key={i.id} item={i} onToggle={() => toggle.mutate(i)} onRemove={() => remove.mutate(i.id)} />)}
          </ul>
        </section>
      ))}

      {done.length > 0 && (
        <section className="mt-8">
          <div className="mb-1 flex items-center justify-between">
            <h2 className="text-xs font-semibold uppercase tracking-wide text-stone-500">In the cart ({done.length})</h2>
            <button className="text-xs text-stone-500 hover:text-ember" onClick={() => clearChecked.mutate()}>Clear</button>
          </div>
          <ul className="divide-y divide-stone-200 rounded-xl bg-white/60 ring-1 ring-stone-200">
            {done.map((i) => <Row key={i.id} item={i} onToggle={() => toggle.mutate(i)} onRemove={() => remove.mutate(i.id)} />)}
          </ul>
        </section>
      )}
    </div>
  )
}

function Row({ item, onToggle, onRemove }: { item: GroceryItem; onToggle: () => void; onRemove: () => void }) {
  return (
    <li className="group flex items-center gap-3 px-3 py-2">
      <input type="checkbox" checked={item.checked} onChange={onToggle} className="h-5 w-5 accent-ember" />
      <div className={`min-w-0 flex-1 ${item.checked ? 'text-stone-400 line-through' : ''}`}>
        <div className="text-sm">
          <span className="font-medium">{item.name}</span>
          {item.amount && <span className="ml-2 text-stone-500">{item.amount}</span>}
        </div>
        {item.sources.length > 0 && <div className="truncate text-xs text-stone-400">{item.sources.join(' · ')}</div>}
      </div>
      <button onClick={onRemove} className="text-stone-300 opacity-0 hover:text-red-600 group-hover:opacity-100" title="Remove">×</button>
    </li>
  )
}
