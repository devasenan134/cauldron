import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { Chip, Empty, PageHeader, Shimmer } from '../components/ui'
import { thumb } from '../format'
import { useDebounced } from '../useDebounced'

export default function Recipes() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const cuisine = params.get('cuisine') ?? ''
  const category = params.get('category') ?? ''
  const debouncedQ = useDebounced(q)

  const set = (key: string, value: string) =>
    setParams((p) => {
      if (value) p.set(key, value)
      else p.delete(key)
      return p
    }, { replace: true })

  const facets = useQuery({ queryKey: ['facets'], queryFn: api.facets })
  const recipes = useQuery({
    queryKey: ['recipes', debouncedQ, cuisine, category],
    queryFn: () => api.recipes({ q: debouncedQ, cuisine, category }),
    placeholderData: (prev) => prev,
  })

  return (
    <div className="rise">
      <PageHeader title="Recipes" subtitle={recipes.data ? `${recipes.data.length} recipes` : 'Loading…'} />

      <div className="relative">
        <span className="pointer-events-none absolute left-5 top-1/2 -translate-y-1/2 text-lg text-stone-400">⌕</span>
        <input
          className="w-full rounded-full bg-paper py-4 pl-12 pr-12 text-base shadow-md shadow-stone-900/5 outline-none ring-1 ring-stone-200 placeholder:text-stone-400 focus:ring-2 focus:ring-ember-bright/50"
          placeholder="Search recipes or ingredients" value={q} onChange={(e) => set('q', e.target.value)} />
        {q && <button onClick={() => set('q', '')} aria-label="Clear search" className="absolute right-4 top-1/2 -translate-y-1/2 rounded-full px-2 text-stone-400 hover:text-ink">✕</button>}
      </div>

      <div className="-mx-5 mt-4 flex gap-2 overflow-x-auto px-5 pb-2 [scrollbar-width:none]">
        <label className={`press relative shrink-0 rounded-full px-4 py-2 text-sm font-semibold ${cuisine ? 'bg-ink text-cream' : 'bg-paper'}`}>
          🌍 {cuisine || 'Cuisine'} ▾
          <select aria-label="Cuisine" className="absolute inset-0 cursor-pointer opacity-0" value={cuisine} onChange={(e) => set('cuisine', e.target.value)}>
            <option value="">All cuisines</option>
            {facets.data?.cuisines.map((c) => <option key={c}>{c}</option>)}
          </select>
        </label>
        <Chip selected={!category} onClick={() => set('category', '')}>All</Chip>
        {facets.data?.categories.map((c) => (
          <Chip key={c} selected={c === category} onClick={() => set('category', c === category ? '' : c)}>{c}</Chip>
        ))}
      </div>

      {recipes.isError && <p className="mt-6 text-red-700">Couldn't load recipes: {String(recipes.error)}</p>}
      {recipes.data?.length === 0 && <Empty emoji="🔍" title="No recipes found" body="Try another word, or clear the filters." />}

      <div className="mt-5 grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
        {recipes.isPending && Array.from({ length: 10 }, (_, i) => (
          <div key={i}><Shimmer className="aspect-square rounded-3xl" /><Shimmer className="mt-3 h-4 w-4/5 rounded-md" /><Shimmer className="mt-2 h-3 w-2/5 rounded-md" /></div>
        ))}
        {recipes.data?.map((r) => (
          <Link key={r.id} to={`/recipes/${r.id}`} className="press group">
            <div className="lift relative aspect-square overflow-hidden rounded-3xl bg-sand">
              {r.image_url && <img src={thumb(r.image_url, 480, 480)} alt="" loading="lazy" className="h-full w-full object-cover transition duration-500 group-hover:scale-105" />}
              {r.kcal_per_serving ? (
                <span className="absolute bottom-2.5 left-2.5 rounded-full bg-white/90 px-2.5 py-1 text-xs font-bold backdrop-blur">{Math.round(r.kcal_per_serving)} kcal</span>
              ) : null}
            </div>
            <h3 className="mt-3 line-clamp-2 font-display text-lg font-bold leading-snug">{r.title}</h3>
            <p className="text-sm text-stone-500">{[r.total_minutes && `${r.total_minutes} min`, r.cuisine].filter(Boolean).join(' · ')}</p>
          </Link>
        ))}
      </div>
    </div>
  )
}
