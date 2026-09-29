import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, KCAL_RANGES, type Facets, type ImportJob, type RecipeFilter } from '../api'
import { Button, Chip, Empty, PageHeader, Shimmer } from '../components/ui'
import { thumb } from '../format'
import { useDebounced } from '../useDebounced'

const SORTS: [RecipeFilter['sort'], string][] = [
  ['title', 'A–Z'], ['quickest', 'Quickest'], ['lowest_kcal', 'Fewest calories'], ['highest_protein', 'Most protein'], ['newest', 'Newest'],
]
const TIMES = [15, 30, 45, 60]
const EMPTY: RecipeFilter = { q: '', cuisines: [], categories: [], tags: [], maxMinutes: null, kcal: null, mine: false, sort: 'title' }

const count = (f: RecipeFilter) =>
  f.cuisines.length + f.categories.length + f.tags.length + (f.maxMinutes ? 1 : 0) + (f.kcal ? 1 : 0) + (f.mine ? 1 : 0)
const toggle = (list: string[], x: string) => (list.includes(x) ? list.filter((y) => y !== x) : [...list, x])

/** The filter lives in the address (?q=…&tag=…), so a filtered list can be bookmarked and Back restores it. */
function useFilter(): [RecipeFilter, (f: RecipeFilter) => void] {
  const [params, setParams] = useSearchParams()
  const f: RecipeFilter = {
    q: params.get('q') ?? '',
    cuisines: params.getAll('cuisine'),
    categories: params.getAll('category'),
    tags: params.getAll('tag'),
    maxMinutes: params.get('max') ? Number(params.get('max')) : null,
    kcal: (params.get('kcal') as RecipeFilter['kcal']) || null,
    mine: params.get('mine') === '1',
    sort: (params.get('sort') as RecipeFilter['sort']) || 'title',
  }
  const set = (n: RecipeFilter) => {
    const p = new URLSearchParams()
    if (n.q) p.set('q', n.q)
    n.cuisines.forEach((c) => p.append('cuisine', c))
    n.categories.forEach((c) => p.append('category', c))
    n.tags.forEach((t) => p.append('tag', t))
    if (n.maxMinutes) p.set('max', String(n.maxMinutes))
    if (n.kcal) p.set('kcal', n.kcal)
    if (n.mine) p.set('mine', '1')
    if (n.sort !== 'title') p.set('sort', n.sort)
    setParams(p, { replace: true })
  }
  return [f, set]
}

export default function Recipes() {
  const [filter, setFilter] = useFilter()
  const [q, setQ] = useState(filter.q)
  const debouncedQ = useDebounced(q, 300)
  const [panel, setPanel] = useState(false)
  // ?import=<link> (e.g. shared from another app) opens the import dialog with it.
  const [importing, setImporting] = useState(() => new URLSearchParams(location.search).has('import'))
  const navigate = useNavigate()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { if (debouncedQ !== filter.q) setFilter({ ...filter, q: debouncedQ }) }, [debouncedQ])

  const facets = useQuery({ queryKey: ['facets'], queryFn: api.facets })
  const recipes = useQuery({ queryKey: ['recipes', filter], queryFn: () => api.recipesFiltered(filter), placeholderData: (prev) => prev })
  const n = count(filter)

  return (
    <div className="rise">
      <PageHeader title="Recipes" subtitle={recipes.data ? `${recipes.data.length} recipes` : 'Loading…'}
        actions={<div className="flex gap-2">
          <Button variant="ghost" onClick={() => setImporting(true)}>🔗 Import</Button>
          <Button onClick={() => navigate('/recipes/new')}>＋ New recipe</Button>
        </div>} />

      <div className="flex items-center gap-2">
        <div className="relative flex-1">
          <span className="pointer-events-none absolute left-5 top-1/2 -translate-y-1/2 text-lg text-stone-400">⌕</span>
          <input
            className="w-full rounded-full bg-paper py-4 pl-12 pr-12 text-base shadow-[0_6px_20px_rgba(0,0,0,0.06)] outline-none ring-1 ring-stone-200 placeholder:text-stone-400 focus:ring-2 focus:ring-ember-bright/50"
            placeholder="Search recipes or ingredients" value={q} onChange={(e) => setQ(e.target.value)} />
          {q && <button onClick={() => setQ('')} aria-label="Clear search" className="absolute right-4 top-1/2 -translate-y-1/2 rounded-full px-2 text-stone-400 hover:text-ink">✕</button>}
        </div>
        <button onClick={() => setPanel(true)} aria-label="Filters"
          className="press relative grid h-[54px] w-[54px] shrink-0 place-items-center rounded-full bg-paper text-xl shadow-[0_6px_20px_rgba(0,0,0,0.06)] ring-1 ring-stone-200">
          ⚙︎
          {n > 0 && <span className="absolute -right-1 -top-1 grid h-5 min-w-5 place-items-center rounded-full bg-ink px-1 text-[11px] font-bold text-cream">{n}</span>}
        </button>
      </div>

      <div className="-mx-5 mt-4 flex gap-2 overflow-x-auto px-5 pb-2 [scrollbar-width:none]">
        <Chip selected={n === 0} onClick={() => setFilter({ ...EMPTY, q: filter.q, sort: filter.sort })}>All</Chip>
        <Chip selected={filter.mine} onClick={() => setFilter({ ...filter, mine: !filter.mine })}>My recipes</Chip>
        {facets.data?.categories.map((c) => (
          <Chip key={c} selected={filter.categories.includes(c)} onClick={() => setFilter({ ...filter, categories: toggle(filter.categories, c) })}>{c}</Chip>
        ))}
      </div>

      {recipes.isError && <p className="mt-6 text-danger">Couldn't load recipes: {String(recipes.error)}</p>}
      {recipes.data?.length === 0 && (filter.mine && n === 1 && !filter.q
        ? <Empty emoji="🧑‍🍳" title="No recipes of your own yet" body="Write one: ingredients, steps and a photo, like the rest of the library."
            action={<Button onClick={() => navigate('/recipes/new')}>＋ New recipe</Button>} />
        : <Empty emoji="🔍" title="No recipes found" body="Try another word, or fewer filters." />)}

      <div className="mt-5 grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
        {recipes.isPending && Array.from({ length: 10 }, (_, i) => (
          <div key={i}><Shimmer className="aspect-square rounded-3xl" /><Shimmer className="mt-3 h-4 w-4/5 rounded-md" /><Shimmer className="mt-2 h-3 w-2/5 rounded-md" /></div>
        ))}
        {recipes.data?.map((r) => (
          <Link key={r.id} to={`/recipes/${r.id}`} className="press group">
            <div className="lift relative grid aspect-square place-items-center overflow-hidden rounded-3xl bg-sand">
              {r.image_url ? <img src={thumb(r.image_url, 480, 480)} alt="" loading="lazy" className="h-full w-full object-cover transition duration-500 group-hover:scale-105" />
                : <span className="text-5xl">🍳</span>}
              {r.kcal_per_serving ? (
                <span className="absolute bottom-2.5 left-2.5 rounded-full bg-white/90 px-2.5 py-1 text-xs font-bold text-black backdrop-blur">{Math.round(r.kcal_per_serving)} kcal</span>
              ) : null}
            </div>
            <h3 className="mt-3 line-clamp-2 font-display text-lg font-bold leading-snug">{r.title}</h3>
            <p className="text-sm text-stone-500">{[r.total_minutes && `${r.total_minutes} min`, r.cuisine].filter(Boolean).join(' · ')}</p>
          </Link>
        ))}
      </div>

      {importing && <ImportDialog initial={new URLSearchParams(location.search).get('import') ?? ''} onClose={() => setImporting(false)} />}
      {panel && <FilterPanel initial={filter} facets={facets.data} onClose={() => setPanel(false)} onApply={(f) => { setFilter({ ...f, q: filter.q }); setPanel(false) }} />}
    </div>
  )
}

function FilterPanel({ initial, facets, onClose, onApply }: { initial: RecipeFilter; facets?: Facets; onClose: () => void; onApply: (f: RecipeFilter) => void }) {
  const [f, setF] = useState(initial)
  const n = count(f)
  useEffect(() => {
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', esc)
    return () => window.removeEventListener('keydown', esc)
  }, [onClose])
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" onClick={onClose}>
      <div className="rise flex h-full w-full max-w-md flex-col bg-cream shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center px-6 pb-2 pt-6">
          <h2 className="flex-1 font-display text-3xl font-extrabold">Filters</h2>
          <button className="font-semibold text-stone-500 hover:text-ink" onClick={() => setF({ ...EMPTY, q: f.q })}>Reset</button>
          <button className="ml-4 text-xl text-stone-500 hover:text-ink" aria-label="Close" onClick={onClose}>✕</button>
        </div>
        <div className="flex-1 overflow-y-auto px-6 pb-4">
          <Group title="Sort by">{SORTS.map(([v, l]) => <Chip key={v} selected={f.sort === v} onClick={() => setF({ ...f, sort: v })}>{l}</Chip>)}</Group>
          {!!facets?.categories.length && (
            <Group title="Meal">{facets.categories.map((c) => <Chip key={c} selected={f.categories.includes(c)} onClick={() => setF({ ...f, categories: toggle(f.categories, c) })}>{c}</Chip>)}</Group>
          )}
          <Group title="Ready in">
            {TIMES.map((m) => <Chip key={m} selected={f.maxMinutes === m} onClick={() => setF({ ...f, maxMinutes: f.maxMinutes === m ? null : m })}>≤ {m} min</Chip>)}
          </Group>
          <Group title="Calories per serving">
            {(Object.keys(KCAL_RANGES) as (keyof typeof KCAL_RANGES)[]).map((k) => (
              <Chip key={k} selected={f.kcal === k} onClick={() => setF({ ...f, kcal: f.kcal === k ? null : k })}>{KCAL_RANGES[k][0]}</Chip>
            ))}
          </Group>
          {facets?.tag_groups.map((g) => (
            <Group key={g.name} title={g.name}>{g.tags.map((t) => <Chip key={t} selected={f.tags.includes(t)} onClick={() => setF({ ...f, tags: toggle(f.tags, t) })}>{t}</Chip>)}</Group>
          ))}
          {!!facets?.cuisines.length && (
            <Group title="Cuisine">{facets.cuisines.map((c) => <Chip key={c} selected={f.cuisines.includes(c)} onClick={() => setF({ ...f, cuisines: toggle(f.cuisines, c) })}>{c}</Chip>)}</Group>
          )}
        </div>
        <div className="border-t border-stone-200 p-5">
          <Button className="w-full py-3.5 text-base" onClick={() => onApply(f)}>
            {n === 0 ? 'Show all recipes' : `Show recipes · ${n} filter${n === 1 ? '' : 's'}`}
          </Button>
        </div>
      </div>
    </div>
  )
}

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="mt-5">
      <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-stone-500">{title}</p>
      <div className="flex flex-wrap gap-2">{children}</div>
    </div>
  )
}

const STEPS: [string, string][] = [['queued', 'Waiting its turn'], ['fetching', "Reading the video's page"],
  ['reading', 'Watching the video and writing the recipe'], ['saving', 'Working out calories']]

/** Import from a YouTube video, a Short or an Instagram Reel; opens the recipe when it's ready. */
function ImportDialog({ initial, onClose }: { initial: string; onClose: () => void }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [url, setUrl] = useState(initial)
  const [job, setJob] = useState<ImportJob | null>(null)
  const [error, setError] = useState<string | null>(null)
  const running = job != null && job.status !== 'done' && job.status !== 'failed'

  const start = async (link: string) => {
    setError(null)
    try {
      let j = await api.startImport(link)
      setJob(j)
      while (j.status !== 'done' && j.status !== 'failed') {
        await new Promise((r) => setTimeout(r, 1500))
        j = await api.importJob(j.id)
        setJob(j)
      }
      if (j.status === 'done' && j.recipe_id) {
        qc.invalidateQueries({ queryKey: ['recipes'] })
        qc.invalidateQueries({ queryKey: ['catalog'] })
        navigate(`/recipes/${j.recipe_id}`)
      } else setError(j.message)
    } catch (e) { setError(String(e).replace(/^Error: POST \/import: \d+ /, '')); setJob(null) }
  }
  const step = Math.max(0, STEPS.findIndex(([s]) => s === job?.status))

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" onClick={() => !running && onClose()}>
      <div className="rise w-full max-w-lg rounded-3xl bg-cream p-6 shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <h2 className="font-display text-3xl font-extrabold">Import a recipe</h2>
        <p className="mt-1 text-sm text-stone-500">From a YouTube video, a Short or an Instagram Reel. Cauldron watches it, reads the description for amounts and macros, and writes the recipe.</p>
        {!running ? (
          <form className="mt-5 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (url.trim()) start(url.trim()) }}>
            <input autoFocus value={url} onChange={(e) => setUrl(e.target.value)} placeholder="Paste a link"
              className="min-w-0 flex-1 rounded-full bg-paper px-5 py-3 outline-none ring-1 ring-stone-200 focus:ring-2 focus:ring-ember-bright/50" />
            <Button variant="accent" type="submit" disabled={!url.trim()}>Import</Button>
          </form>
        ) : (
          <div className="mt-5 rounded-2xl bg-paper p-5 ring-1 ring-stone-200">
            <p className="flex items-center gap-3 font-semibold"><span className="h-4 w-4 animate-spin rounded-full border-2 border-ember-bright border-t-transparent" />{STEPS[step][1]}…</p>
            <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-stone-200">
              <div className="h-full rounded-full bg-ember-bright transition-[width] duration-500" style={{ width: `${((step + 1) / (STEPS.length + 1)) * 100}%` }} />
            </div>
            <p className="mt-3 text-xs text-stone-500">This takes about a minute. You can close this page; it keeps going and shows up in My recipes.</p>
          </div>
        )}
        {error && <p className="mt-4 text-sm font-semibold text-danger">{error}</p>}
      </div>
    </div>
  )
}
