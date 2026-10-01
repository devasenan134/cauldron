import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, KCAL_RANGES, TIME_RANGES, type Facets, type ImportJob, type RecipeFilter, type TagGroup } from '../api'
import { Button, Chip, Empty, PageHeader, Shimmer } from '../components/ui'
import { thumb } from '../format'
import { useDebounced } from '../useDebounced'

const SORTS: [RecipeFilter['sort'], string][] = [
  ['title', 'A–Z'], ['quickest', 'Quickest'], ['lowest_kcal', 'Fewest calories'], ['highest_protein', 'Most protein'], ['newest', 'Newest'],
]
const EMPTY: RecipeFilter = { q: '', cuisines: [], categories: [], tags: [], time: null, kcal: null, mine: false, prep: false, sort: 'title' }
// Links from before "Ready in" was the only time filter: ?max=30, or Cook Well's time tags.
const OLD_TIME_TAGS: Record<string, RecipeFilter['time']> = { Quick: '30', 'Under 1 Hour': '60', 'I Got Time': 'long' }

const count = (f: RecipeFilter) =>
  f.cuisines.length + f.categories.length + f.tags.length + (f.time ? 1 : 0) + (f.kcal ? 1 : 0) + (f.mine ? 1 : 0) + (f.prep ? 1 : 0)
const toggle = (list: string[], x: string) => (list.includes(x) ? list.filter((y) => y !== x) : [...list, x])
const oneOf = <T extends string>(options: Record<T, unknown>, x: string | null): T | null =>
  x != null && Object.hasOwn(options, x) ? (x as T) : null

/** Pick or unpick a tag; in a pick-one family (Difficulty) picking one drops the family's others. */
function toggleTag(f: RecipeFilter, group: TagGroup, t: string): RecipeFilter {
  if (group.mode !== 'one' || f.tags.includes(t)) return { ...f, tags: toggle(f.tags, t) }
  return { ...f, tags: [...f.tags.filter((x) => !group.tags.includes(x)), t] }
}

/** The filter lives in the address (?q=…&tag=…), so a filtered list can be bookmarked and Back restores it.
 *  Anything in the address that isn't an option is ignored rather than trusted. */
function useFilter(): [RecipeFilter, (f: RecipeFilter) => void] {
  const [params, setParams] = useSearchParams()
  const tags = params.getAll('tag').filter(Boolean)
  const oldMax = Number(params.get('max'))
  const f: RecipeFilter = {
    q: params.get('q') ?? '',
    cuisines: params.getAll('cuisine').filter(Boolean),
    categories: params.getAll('category').filter(Boolean),
    tags: tags.filter((t) => !(t in OLD_TIME_TAGS)),
    time: oneOf(TIME_RANGES, params.get('time'))
      ?? (oldMax ? oneOf(TIME_RANGES, String(oldMax)) : null)
      ?? tags.map((t) => OLD_TIME_TAGS[t]).find(Boolean) ?? null,
    kcal: oneOf(KCAL_RANGES, params.get('kcal')),
    mine: params.get('mine') === '1',
    prep: params.get('prep') === '1',
    sort: SORTS.find(([v]) => v === params.get('sort'))?.[0] ?? 'title',
  }
  const set = (n: RecipeFilter) => {
    const p = new URLSearchParams()
    if (n.q) p.set('q', n.q)
    n.cuisines.forEach((c) => p.append('cuisine', c))
    n.categories.forEach((c) => p.append('category', c))
    n.tags.forEach((t) => p.append('tag', t))
    if (n.time) p.set('time', n.time)
    if (n.kcal) p.set('kcal', n.kcal)
    if (n.mine) p.set('mine', '1')
    if (n.prep) p.set('prep', '1')
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
  // The address changed by itself (Back, a link): show its search in the box.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { if (filter.q !== debouncedQ) setQ(filter.q) }, [filter.q])

  const facets = useQuery({ queryKey: ['recipes', 'facets', filter], queryFn: () => api.facets(filter), placeholderData: (prev) => prev })
  const recipes = useQuery({ queryKey: ['recipes', filter], queryFn: () => api.recipesFiltered(filter), placeholderData: (prev) => prev })
  const n = count(filter)
  const clearFilters = () => setFilter({ ...EMPTY, q: filter.q, sort: filter.sort })

  return (
    <div className="rise">
      <PageHeader title="Recipes" subtitle={recipes.data ? `${recipes.data.length} recipe${recipes.data.length === 1 ? '' : 's'}` : 'Loading…'}
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
        <Chip selected={n === 0} onClick={clearFilters}>All</Chip>
        <Chip selected={filter.mine} onClick={() => setFilter({ ...filter, mine: !filter.mine })}>My recipes</Chip>
        <Chip selected={filter.prep} onClick={() => setFilter({ ...filter, prep: !filter.prep })}>🫙 Prepped</Chip>
        {facets.data?.categories.map((c) => (
          <Chip key={c} selected={filter.categories.includes(c)} disabled={facets.data?.counts?.category[c] === 0 && !filter.categories.includes(c)}
            onClick={() => setFilter({ ...filter, categories: toggle(filter.categories, c) })}>{c}</Chip>
        ))}
      </div>

      {recipes.isError && <p className="mt-6 text-danger">Couldn't load recipes: {String(recipes.error)}</p>}
      {recipes.data?.length === 0 && (filter.prep && n === 1 && !filter.q
        ? <Empty emoji="🫙" title="No prepped ingredients yet"
            body="Mark a recipe like cooked rice, pickled onions or a sauce as a prepped ingredient (on its page), and other recipes can use it by weight." />
        : filter.mine && n === 1 && !filter.q
        ? <Empty emoji="🧑‍🍳" title="No recipes of your own yet" body="Write one: ingredients, steps and a photo, like the rest of the library."
            action={<Button onClick={() => navigate('/recipes/new')}>＋ New recipe</Button>} />
        : n === 0 && !filter.q
        ? <Empty emoji="🍲" title="Your recipe book is empty"
            body="Import a recipe from a YouTube video, an Instagram Reel, a web page or a PDF, or write your own."
            action={<div className="flex flex-wrap justify-center gap-2">
              <Button onClick={() => setImporting(true)}>🔗 Import</Button>
              <Button variant="ghost" onClick={() => navigate('/recipes/new')}>＋ New recipe</Button>
            </div>} />
        : <Empty emoji="🔍" title="No recipes found"
            body={filter.q && n ? `Nothing matches “${filter.q}” with these filters.` : filter.q ? `Nothing matches “${filter.q}”.` : 'Nothing matches all of these filters.'}
            action={<div className="flex flex-wrap justify-center gap-2">
              {n > 0 && <Button onClick={clearFilters}>Clear filters</Button>}
              {filter.q && <Button variant="ghost" onClick={() => setQ('')}>Clear search</Button>}
            </div>} />)}

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
              {r.is_prep && <span className="absolute left-2.5 top-2.5 rounded-full bg-ember-bright px-2.5 py-1 text-xs font-bold text-on-go">🫙 Prep</span>}
            </div>
            <h3 className="mt-3 line-clamp-2 font-display text-lg font-bold leading-snug">{r.title}</h3>
            <p className="text-sm text-stone-500">{[r.total_minutes && `${r.total_minutes} min`, r.cuisine].filter(Boolean).join(' · ')}</p>
          </Link>
        ))}
      </div>

      {importing && <ImportDialog initial={new URLSearchParams(location.search).get('import') ?? ''} onClose={() => setImporting(false)} />}
      {panel && <FilterPanel initial={filter} onClose={() => setPanel(false)} onApply={(f) => { setFilter({ ...f, q: filter.q }); setPanel(false) }} />}
    </div>
  )
}

const MODE_HINT = { one: 'pick one', any: 'any of these', all: 'all you pick' } as const

/** The filters, with how many recipes each option would show given the rest: options that would show
 *  none are greyed out, so a combination that can't match anything can't be picked. */
function FilterPanel({ initial, onClose, onApply }: { initial: RecipeFilter; onClose: () => void; onApply: (f: RecipeFilter) => void }) {
  const [f, setF] = useState(initial)
  const n = count(f)
  const facets = useQuery({ queryKey: ['recipes', 'facets', f], queryFn: () => api.facets(f), placeholderData: (prev) => prev })
  const data: Facets | undefined = facets.data
  const counts = data?.counts
  useEffect(() => {
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', esc)
    return () => window.removeEventListener('keydown', esc)
  }, [onClose])
  // One chip: its count (if known) and greyed out when picking it would show nothing.
  const option = (key: string, label: string, selected: boolean, n: number | undefined, onClick: () => void) => (
    <Chip key={key} selected={selected} disabled={!selected && n === 0} onClick={onClick}>
      {label}{n != null && <span className={`ml-1.5 font-normal ${selected ? 'text-cream/70' : 'text-stone-400'}`}>{n}</span>}
    </Chip>
  )
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" onClick={onClose}>
      <div className="rise flex h-full w-full max-w-md flex-col bg-cream shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center px-6 pb-2 pt-[max(1.5rem,env(safe-area-inset-top))]">
          <h2 className="flex-1 font-display text-3xl font-extrabold">Filters</h2>
          <button className="font-semibold text-stone-500 hover:text-ink" onClick={() => setF({ ...EMPTY, q: f.q })}>Reset</button>
          <button className="ml-4 p-1 text-xl text-stone-500 hover:text-ink" aria-label="Close" onClick={onClose}>✕</button>
        </div>
        <div className="flex-1 overflow-y-auto overscroll-contain px-6 pb-4">
          <Group title="Sort by">{SORTS.map(([v, l]) => <Chip key={v} selected={f.sort === v} onClick={() => setF({ ...f, sort: v })}>{l}</Chip>)}</Group>
          {!!data?.categories.length && (
            <Group title="Meal" hint={MODE_HINT.any}>{data.categories.map((c) =>
              option(c, c, f.categories.includes(c), counts?.category[c] ?? 0, () => setF({ ...f, categories: toggle(f.categories, c) })))}</Group>
          )}
          <Group title="Ready in" hint={MODE_HINT.one}>
            {(Object.keys(TIME_RANGES) as (keyof typeof TIME_RANGES)[]).map((k) =>
              option(k, TIME_RANGES[k], f.time === k, counts?.time[k], () => setF({ ...f, time: f.time === k ? null : k })))}
          </Group>
          <Group title="Calories per serving" hint={MODE_HINT.one}>
            {(Object.keys(KCAL_RANGES) as (keyof typeof KCAL_RANGES)[]).map((k) =>
              option(k, KCAL_RANGES[k], f.kcal === k, counts?.kcal[k], () => setF({ ...f, kcal: f.kcal === k ? null : k })))}
          </Group>
          {data?.tag_groups.map((g) => (
            <Group key={g.name} title={g.name} hint={MODE_HINT[g.mode]}>{g.tags.map((t) =>
              option(t, t, f.tags.includes(t), counts?.tag[t] ?? 0, () => setF(toggleTag(f, g, t))))}</Group>
          ))}
          {!!data?.cuisines.length && (
            <Group title="Cuisine" hint={MODE_HINT.any}>{data.cuisines.map((c) =>
              option(c, c, f.cuisines.includes(c), counts?.cuisine[c] ?? 0, () => setF({ ...f, cuisines: toggle(f.cuisines, c) })))}</Group>
          )}
        </div>
        <div className="border-t border-stone-200 p-5 pb-[max(1.25rem,env(safe-area-inset-bottom))]">
          <Button className="w-full py-3.5 text-base" onClick={() => onApply(f)}>
            {data?.total == null ? (n === 0 ? 'Show all recipes' : `Show recipes · ${n} filter${n === 1 ? '' : 's'}`)
              : data.total === 0 ? 'No recipes match' : `Show ${data.total} recipe${data.total === 1 ? '' : 's'}`}
          </Button>
        </div>
      </div>
    </div>
  )
}

function Group({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
  return (
    <div className="mt-5">
      <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-stone-500">
        {title}{hint && <span className="ml-2 font-normal normal-case tracking-normal text-stone-400">{hint}</span>}
      </p>
      <div className="flex flex-wrap gap-2">{children}</div>
    </div>
  )
}

// The server says what it's doing in job.message; these are for when it hasn't yet.
const STEPS: [string, string][] = [['queued', 'Waiting its turn'], ['fetching', 'Opening the link'],
  ['reading', 'Writing the recipe'], ['saving', 'Working out calories']]
const FILES = '.pdf,image/*,.yaml,.yml,.json,.txt,.md'

/** Import from a recipe page, a video (YouTube, Shorts, Reels) or a file (PDF, photo, YAML/JSON/text);
 *  opens the recipe when it's ready. */
function ImportDialog({ initial, onClose }: { initial: string; onClose: () => void }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [url, setUrl] = useState(initial)
  const [job, setJob] = useState<ImportJob | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [over, setOver] = useState(false)
  const running = job != null && job.status !== 'done' && job.status !== 'failed'

  const start = async (from: string | File) => {
    setError(null)
    try {
      let j = typeof from === 'string' ? await api.startImport(from) : await api.importFile(from)
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
    } catch (e) { setError(String(e).replace(/^Error: (POST \/import: )?\d+ /, '').replace(/^\{"detail":"(.*)"\}$/, '$1')); setJob(null) }
  }
  const step = Math.max(0, STEPS.findIndex(([s]) => s === job?.status))

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" onClick={() => !running && onClose()}>
      <div className={`rise w-full max-w-lg rounded-3xl bg-cream p-6 shadow-2xl ${over ? 'ring-4 ring-ember-bright/60' : ''}`} onClick={(e) => e.stopPropagation()}
        onDragOver={(e) => { if (!running) { e.preventDefault(); setOver(true) } }} onDragLeave={() => setOver(false)}
        onDrop={(e) => { e.preventDefault(); setOver(false); const f = e.dataTransfer.files[0]; if (f && !running) start(f) }}>
        <h2 className="font-display text-3xl font-extrabold">Import a recipe</h2>
        <p className="mt-1 text-sm text-stone-500">From a recipe website, a YouTube video, a Short or an Instagram Reel, or a file: a PDF, a photo of a recipe, or a YAML/JSON recipe. Cauldron reads it, keeps the amounts and macros it gives, and writes the recipe.</p>
        {!running ? (
          <>
            <form className="mt-5 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (url.trim()) start(url.trim()) }}>
              <input autoFocus value={url} onChange={(e) => setUrl(e.target.value)} placeholder="Paste a link"
                className="min-w-0 flex-1 rounded-full bg-paper px-5 py-3 outline-none ring-1 ring-stone-200 focus:ring-2 focus:ring-ember-bright/50" />
              <Button variant="accent" type="submit" disabled={!url.trim()}>Import</Button>
            </form>
            <label className="mt-3 flex cursor-pointer items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-stone-300 px-4 py-4 text-sm font-semibold text-stone-500 hover:border-stone-400 hover:text-ink">
              📄 Choose a PDF, photo or recipe file <span className="hidden font-normal sm:inline">(or drop it here)</span>
              <input type="file" accept={FILES} className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) start(f); e.target.value = '' }} />
            </label>
          </>
        ) : (
          <div className="mt-5 rounded-2xl bg-paper p-5 ring-1 ring-stone-200">
            <p className="flex items-center gap-3 font-semibold"><span className="h-4 w-4 animate-spin rounded-full border-2 border-ember-bright border-t-transparent" />{job?.message || STEPS[step][1]}…</p>
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
