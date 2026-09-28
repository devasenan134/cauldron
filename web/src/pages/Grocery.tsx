import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, type GroceryItem, type GroceryTemplate } from '../api'
import { Button, Empty } from '../components/ui'
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
  const [templatesOpen, setTemplatesOpen] = useState(false)
  const navigate = useNavigate()
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
      <div className="mb-6 flex items-end gap-3">
        <button onClick={() => navigate(-1)} aria-label="Back" className="press mb-1 grid h-11 w-11 place-items-center rounded-full bg-paper ring-1 ring-stone-200">←</button>
        <div className="flex-1">
          <p className="text-sm text-stone-500">{list.length ? `${done.length} of ${list.length} in the cart` : 'Your list'}</p>
          <h1 className="font-display text-4xl font-extrabold sm:text-5xl">Grocery</h1>
        </div>
        <Button variant="ghost" onClick={() => setTemplatesOpen(true)}>☰ Templates</Button>
      </div>
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
      {templatesOpen && <Templates onClose={() => setTemplatesOpen(false)} onApplied={(l) => qc.setQueryData(['grocery'], l)} />}
    </div>
  )
}

/** Saved lists ("Weekly basics"): add one to your list in a click, or save the current list as one. */
function Templates({ onClose, onApplied }: { onClose: () => void; onApplied: (l: GroceryItem[]) => void }) {
  const qc = useQueryClient()
  const templates = useQuery({ queryKey: ['templates'], queryFn: api.templates })
  const [editing, setEditing] = useState<GroceryTemplate | 'new' | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const reload = () => qc.invalidateQueries({ queryKey: ['templates'] })
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" onClick={onClose}>
      <div className="rise flex h-full w-full max-w-md flex-col overflow-y-auto bg-cream p-6 shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center">
          <h2 className="flex-1 font-display text-3xl font-extrabold">Templates</h2>
          <button className="text-xl text-stone-500 hover:text-ink" aria-label="Close" onClick={onClose}>✕</button>
        </div>
        <p className="mt-1 text-sm text-stone-500">Lists you buy again and again. Add one to your grocery list in a click.</p>
        {msg && <p className="mt-3 font-semibold text-ember">{msg}</p>}
        {templates.data?.length === 0 && <p className="mt-6 text-stone-500">No templates yet.</p>}
        {templates.data?.map((t) => (
          <div key={t.id} className="mt-3 flex items-center gap-3 rounded-2xl bg-paper p-4 ring-1 ring-stone-200">
            <button className="min-w-0 flex-1 text-left" onClick={() => setEditing(t)}>
              <p className="font-semibold">{t.name}</p>
              <p className="truncate text-sm text-stone-500">{t.items.map((i) => i.name).join(', ') || 'Empty'}</p>
            </button>
            <Button onClick={async () => { onApplied(await api.applyTemplate(t.id)); setMsg(`Added “${t.name}” to your list`) }}>Add</Button>
          </div>
        ))}
        <div className="mt-5 flex flex-wrap gap-2">
          <Button variant="ghost" onClick={() => setEditing('new')}>＋ New template</Button>
          <Button variant="ghost" onClick={async () => {
            const name = prompt('Name this template', 'Weekly basics')
            if (name?.trim()) { await api.templateFromList(name.trim()); setMsg(`Saved “${name.trim()}”`); reload() }
          }}>Save my list as one</Button>
        </div>
        {editing && <TemplateEditor t={editing === 'new' ? null : editing} onDone={() => { setEditing(null); reload() }} />}
      </div>
    </div>
  )
}

function TemplateEditor({ t, onDone }: { t: GroceryTemplate | null; onDone: () => void }) {
  const [name, setName] = useState(t?.name ?? '')
  const [lines, setLines] = useState(t?.items.map((i) => [i.name, i.amount].filter(Boolean).join(' ')).join('\n') ?? '')
  const save = async () => {
    if (!name.trim()) return
    await api.saveTemplate(t?.id ?? null, name.trim(), lines.split('\n').map((l) => l.trim()).filter(Boolean).map(parseQuickAdd))
    onDone()
  }
  return (
    <div className="mt-5 rounded-3xl bg-paper p-4 ring-1 ring-stone-200">
      <p className="font-display text-xl font-bold">{t ? 'Edit template' : 'New template'}</p>
      <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Name, e.g. Weekly basics"
        className="mt-3 w-full rounded-xl bg-cream px-3 py-2.5 outline-none ring-1 ring-stone-200 focus:ring-2 focus:ring-ember-bright/50" />
      <textarea value={lines} onChange={(e) => setLines(e.target.value)} rows={6} placeholder={'Items, one per line\nMilk 1 L\nEggs 12\nBananas'}
        className="mt-2 w-full rounded-xl bg-cream px-3 py-2.5 outline-none ring-1 ring-stone-200 focus:ring-2 focus:ring-ember-bright/50" />
      <div className="mt-3 flex gap-2">
        <Button variant="accent" onClick={save}>Save</Button>
        <Button variant="ghost" onClick={onDone}>Cancel</Button>
        {t && <button className="ml-auto font-semibold text-danger" onClick={async () => { if (confirm(`Delete “${t.name}”?`)) { await api.deleteTemplate(t.id); onDone() } }}>Delete</button>}
      </div>
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
