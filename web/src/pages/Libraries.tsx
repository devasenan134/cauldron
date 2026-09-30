import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type LibraryName, type LibraryRecipe } from '../api'
import { useMe } from '../auth'
import { Button, Pill, Shimmer, inputCls } from '../components/ui'
import { kcal, plural, thumb } from '../format'

const TABS: { name: LibraryName; label: string; note: string }[] = [
  { name: 'everyone', label: 'Everyone', note: 'Every user sees these, from the day they sign up. Hide one and nobody sees it; it stays here to show again.' },
  { name: 'guests', label: 'Guests', note: 'Only you and the guests in CAULDRON_ALLOWED_EMAILS see these (the Cook Well recipes). They never go to everyone.' },
]

/** Settings → Recipe libraries (the owner only): the recipes everyone gets, and the ones for guests. */
export default function Libraries() {
  const me = useMe().data
  const [tab, setTab] = useState<LibraryName>('everyone')
  const [q, setQ] = useState('')
  const [adding, setAdding] = useState(false)
  const qc = useQueryClient()
  const list = useQuery({ queryKey: ['library', tab], queryFn: () => api.library(tab), enabled: !!me?.is_owner })
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['library'] })
    qc.invalidateQueries({ queryKey: ['recipes'] })
    qc.invalidateQueries({ queryKey: ['catalog'] })
  }
  if (me && !me.is_owner) return <p className="text-stone-500">Only the owner manages the recipe libraries.</p>

  const shown = (list.data ?? []).filter((r) => r.title.toLowerCase().includes(q.trim().toLowerCase()))
  const hidden = (list.data ?? []).filter((r) => r.hidden).length
  const info = TABS.find((t) => t.name === tab)!

  return (
    <div className="rise mx-auto max-w-3xl">
      <Link to="/settings" className="press inline-flex items-center gap-1 rounded-full bg-paper px-4 py-2 text-sm font-semibold ring-1 ring-stone-200">← Settings</Link>
      <h1 className="mt-4 font-display text-4xl font-extrabold sm:text-5xl">Recipe libraries</h1>

      <div className="mt-6 flex rounded-full bg-sand p-1">
        {TABS.map((t) => (
          <button key={t.name} onClick={() => { setTab(t.name); setQ('') }}
            className={`press flex-1 rounded-full py-2.5 text-sm transition-colors ${tab === t.name ? 'bg-paper font-bold shadow-sm' : 'font-medium text-stone-500'}`}>
            {t.label}
          </button>
        ))}
      </div>
      <p className="mt-3 text-sm text-stone-500">{info.note}</p>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search this library" className={`${inputCls} min-w-0 flex-1`} />
        <Button variant="accent" onClick={() => setAdding(true)}>＋ Add one of mine</Button>
      </div>
      {list.data && <p className="mt-3 text-sm text-stone-500">{plural(list.data.length, 'recipe')}{hidden ? ` · ${hidden} hidden` : ''}</p>}

      <div className="mt-2 divide-y divide-stone-200 rounded-3xl bg-paper px-4 ring-1 ring-stone-200">
        {list.isPending && [0, 1, 2].map((i) => <Shimmer key={i} className="my-3 h-14 rounded-2xl" />)}
        {list.isError && <p className="py-4 text-danger">Couldn't load it: {String(list.error)}</p>}
        {list.data && !shown.length && <p className="py-6 text-center text-stone-500">{q ? 'Nothing matches.' : 'This library is empty.'}</p>}
        {shown.map((r) => <Row key={r.id} r={r} onChange={refresh} />)}
      </div>

      {adding && <AddDialog library={tab} onClose={() => setAdding(false)} onAdded={() => { setAdding(false); refresh() }} />}
    </div>
  )
}

function Row({ r, onChange }: { r: LibraryRecipe; onChange: () => void }) {
  const [error, setError] = useState<string | null>(null)
  const act = useMutation({
    mutationFn: async (what: 'hide' | 'show' | 'back'): Promise<unknown> =>
      what === 'back' ? api.takeBackFromLibrary(r.id) : api.setLibraryHidden(r.id, what === 'hide'),
    onSuccess: onChange,
    onError: (e: Error) => setError(reason(e)),
  })
  return (
    <div className={`flex flex-wrap items-center gap-3 py-3 ${r.hidden ? 'opacity-60' : ''}`}>
      <Link to={`/recipes/${r.id}`} className="flex min-w-0 flex-1 items-center gap-3">
        {r.image_url ? <img src={thumb(r.image_url, 120, 120)} alt="" className="h-14 w-14 shrink-0 rounded-2xl object-cover" />
          : <div className="grid h-14 w-14 shrink-0 place-items-center rounded-2xl bg-ember-soft text-2xl">🍳</div>}
        <div className="min-w-0">
          <p className="truncate font-semibold">{r.title}</p>
          <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-stone-500">
            <span>{kcal(r.kcal_per_serving)}</span>
            {r.cuisine && <span>· {r.cuisine}</span>}
            {r.hidden && <Pill tone="ink">Hidden</Pill>}
            {r.edited && <Pill tone="sky">Edited here</Pill>}
            {r.yours && <Pill tone="ember">Yours</Pill>}
          </div>
        </div>
      </Link>
      <div className="flex flex-wrap gap-2">
        <Link to={`/recipes/${r.id}/edit`}><Button variant="ghost">Edit</Button></Link>
        <Button variant="ghost" disabled={act.isPending} onClick={() => act.mutate(r.hidden ? 'show' : 'hide')}>{r.hidden ? 'Show' : 'Hide'}</Button>
        {r.yours && <Button variant="ghost" disabled={act.isPending} onClick={() => act.mutate('back')}>Back to my recipes</Button>}
      </div>
      {error && <p className="w-full text-sm text-danger">{error}</p>}
    </div>
  )
}

/** Pick one of your own recipes to put in the library. */
function AddDialog({ library, onClose, onAdded }: { library: LibraryName; onClose: () => void; onAdded: () => void }) {
  const [q, setQ] = useState('')
  const [error, setError] = useState<string | null>(null)
  const mine = useQuery({ queryKey: ['recipes', 'mine-for-library'], queryFn: () => api.recipesFiltered({
    q: '', cuisines: [], categories: [], tags: [], maxMinutes: null, kcal: null, mine: true, prep: false, sort: 'title' }) })
  const add = useMutation({ mutationFn: (id: number) => api.addToLibrary(library, id), onSuccess: onAdded, onError: (e: Error) => setError(reason(e)) })
  const shown = (mine.data ?? []).filter((r) => r.title.toLowerCase().includes(q.trim().toLowerCase()))
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" onClick={onClose}>
      <div className="rise flex max-h-[80vh] w-full max-w-md flex-col rounded-3xl bg-cream p-6 shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <h3 className="font-display text-2xl font-bold">Add to {library === 'everyone' ? 'Everyone' : 'Guests'}</h3>
        <p className="mt-1 text-sm text-stone-500">
          It moves out of your recipes into the library, and you can take it back later.
          {library === 'everyone' && ' Imported recipes and versions of Cook Well recipes are other people\'s work, so they can\'t go here.'}
        </p>
        <input autoFocus value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search your recipes" className={`${inputCls} mt-3`} />
        {error && <p className="mt-2 text-sm text-danger">{error}</p>}
        <div className="mt-3 min-h-0 flex-1 overflow-y-auto">
          {mine.isPending && <p className="text-stone-500">Loading…</p>}
          {mine.data && !shown.length && <p className="text-stone-500">No recipes of your own{q ? ' match' : ' yet'}.</p>}
          {shown.map((r) => (
            <button key={r.id} disabled={add.isPending} onClick={() => { setError(null); add.mutate(r.id) }}
              className="flex w-full items-center gap-3 rounded-2xl px-2 py-2 text-left hover:bg-sand disabled:opacity-50">
              {r.image_url ? <img src={thumb(r.image_url, 80, 80)} alt="" className="h-10 w-10 rounded-xl object-cover" />
                : <div className="grid h-10 w-10 place-items-center rounded-xl bg-ember-soft">🍳</div>}
              <span className="flex-1 truncate font-semibold">{r.title}</span>
              <span className="text-sm font-semibold text-ember">Add</span>
            </button>
          ))}
        </div>
        <div className="mt-4 flex justify-end"><Button variant="ghost" onClick={onClose}>Done</Button></div>
      </div>
    </div>
  )
}

function reason(e: Error): string {
  return e.message.match(/"detail":\s*"([^"]*)"/)?.[1] ?? 'Something went wrong. Please try again.'
}
