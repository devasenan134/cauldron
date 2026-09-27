import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { inputCls, kcal, Pill, thumb } from '../components/ui'
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
    <div>
      <div className="mb-5 flex flex-wrap items-center gap-2">
        <input
          className={`${inputCls} w-full sm:w-72`}
          placeholder="Search recipes or ingredients…"
          value={q}
          onChange={(e) => set('q', e.target.value)}
        />
        <select className={inputCls} value={cuisine} onChange={(e) => set('cuisine', e.target.value)}>
          <option value="">All cuisines</option>
          {facets.data?.cuisines.map((c) => <option key={c}>{c}</option>)}
        </select>
        <select className={inputCls} value={category} onChange={(e) => set('category', e.target.value)}>
          <option value="">All meals</option>
          {facets.data?.categories.map((c) => <option key={c}>{c}</option>)}
        </select>
        <span className="text-sm text-stone-500">{recipes.data?.length ?? '…'} recipes</span>
      </div>

      {recipes.isError && <p className="text-red-700">Couldn't load recipes: {String(recipes.error)}</p>}

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
        {recipes.data?.map((r) => (
          <Link
            key={r.id}
            to={`/recipes/${r.id}`}
            className="group overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-stone-200 transition hover:shadow-md"
          >
            <div className="aspect-[4/3] overflow-hidden bg-stone-100">
              {r.image_url && (
                <img
                  src={thumb(r.image_url, 400, 300)}
                  alt=""
                  loading="lazy"
                  className="h-full w-full object-cover transition group-hover:scale-105"
                />
              )}
            </div>
            <div className="space-y-1.5 p-3">
              <h3 className="line-clamp-2 text-sm font-semibold leading-snug">{r.title}</h3>
              <div className="flex flex-wrap gap-1">
                {r.kcal_per_serving ? <Pill tone="ember">{kcal(r.kcal_per_serving)}/serving</Pill> : null}
                {r.total_minutes ? <Pill>{r.total_minutes} min</Pill> : null}
                {r.cuisine && <Pill>{r.cuisine}</Pill>}
              </div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
