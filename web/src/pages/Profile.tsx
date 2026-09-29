import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, type FolderSummary, type Me, type RecipeSummary } from '../api'
import { ME, useMe } from '../auth'
import { Button, Empty, Shimmer } from '../components/ui'
import { addDays, dayLabel, today, weekdayLong, weekStart } from '../dates'
import { kcal, num, plural, thumb } from '../format'

const WEEKS = 20

/** Your profile, like Cook Well's: who you are, how you've been cooking, then Cooked and Catalog. */
export default function Profile() {
  const me = useMe().data
  const qc = useQueryClient()
  const profile = useQuery({ queryKey: ['profile'], queryFn: api.profile })
  const cooked = useQuery({ queryKey: ['cooked'], queryFn: api.cooked })
  const catalog = useQuery({ queryKey: ['catalog'], queryFn: api.catalog })
  const [tab, setTab] = useState<'cooked' | 'catalog'>('catalog')
  const [view, setView] = useCatalogView()
  const newFolder = useMutation({
    mutationFn: api.createFolder,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['catalog'] }),
  })
  const p = profile.data
  const c = catalog.data

  return (
    <div className="rise mx-auto max-w-4xl">
      <div className="mb-6 flex items-center">
        <h1 className="flex-1 font-display text-4xl font-extrabold sm:text-5xl">Profile</h1>
        <Link to="/ingredients" title="Ingredients and their macros"
          className="press mr-2 inline-flex h-11 items-center gap-1.5 rounded-full bg-paper px-4 font-semibold shadow-[0_4px_16px_rgba(0,0,0,0.08)] ring-1 ring-stone-200">🥕 Ingredients</Link>
        <Link to="/settings" aria-label="Settings" title="Settings"
          className="press grid h-11 w-11 place-items-center rounded-full bg-paper text-xl shadow-[0_4px_16px_rgba(0,0,0,0.08)] ring-1 ring-stone-200">⚙︎</Link>
      </div>

      <div className="flex flex-wrap items-center gap-5">
        <div className="grid h-24 w-24 place-items-center rounded-full bg-ember-soft font-display text-5xl font-extrabold text-ember">
          {(me?.name || me?.email || '?').charAt(0).toUpperCase()}
        </div>
        <div>
          <p className="font-display text-3xl font-bold">{me?.name || 'You'}</p>
          <div className="mt-2 flex gap-6">
            <Stat n={p?.cooked} label="cooked" dot="bg-ember-bright" />
            <Stat n={p?.mine} label="my recipes" dot="bg-amber-deep" />
            <Stat n={p?.favorites} label="favorites" dot="bg-pink-deep" />
          </div>
        </div>
        {!!p?.streak && <span className="rounded-full bg-orange-500 px-4 py-1.5 font-bold text-white">🔥 {plural(p.streak, 'day')} in a row</span>}
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <div className="rounded-3xl p-5 ring-1 ring-stone-200">{p ? <Calendar days={p.days} out={p.out_days ?? []} /> : <Shimmer className="h-40 rounded-2xl" />}</div>
        {p && (p.cuisines.length > 0 || p.categories.length > 0) && (
          <div className="rounded-3xl p-5 ring-1 ring-stone-200">
            <p className="font-display text-xl font-bold">Your kitchen lately</p>
            <div className="mt-3 flex flex-wrap gap-2">
              {[...p.cuisines, ...p.categories].map(([name, n]) => <span key={name} className="rounded-full bg-sand px-3 py-1 text-sm font-semibold">{name} · {n}×</span>)}
            </div>
          </div>
        )}
      </div>

      <div className="mt-8 flex rounded-full bg-sand p-1 sm:inline-flex">
        {(['catalog', 'cooked'] as const).map((t) => (
          <button key={t} onClick={() => setTab(t)}
            className={`press flex-1 rounded-full px-6 py-2 capitalize sm:flex-none ${tab === t ? 'bg-paper font-bold shadow-sm' : 'font-medium text-stone-500'}`}>{t}</button>
        ))}
      </div>

      {tab === 'cooked' ? (
        <div className="mt-4">
          {cooked.isPending && <Shimmer className="h-20 rounded-2xl" />}
          {cooked.data?.length === 0 && <Empty emoji="🍳" title="Nothing cooked yet" body="Meals on your plan count as cooked once their day comes." />}
          {Object.entries(groupBy(cooked.data ?? [], (x) => x.day)).map(([day, meals]) => (
            <div key={day} className="mt-4">
              <p className="mb-1 font-semibold text-stone-500">
                {day === today() ? 'Today' : day === addDays(today(), -1) ? 'Yesterday' : `${weekdayLong(day)}, ${dayLabel(day).date}`}
              </p>
              {meals.map((m) => <Row key={m.entry_id} r={m.recipe} sub={m.batch != null ? `Batch · cooked ${num(m.batch)}` : plural(m.servings, 'serving')} />)}
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-4">
          {/* Favorites, your folders and a new one: as tiles (grid) or rows (list). */}
          <div className="flex items-center">
            <p className="flex-1 text-sm text-stone-500">Your own recipes are under Recipes → My recipes.</p>
            <ViewToggle view={view} setView={setView} />
          </div>
          {!c ? <Shimmer className="mt-4 h-20 rounded-2xl" /> : view === 'grid' ? (
            <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
              <FolderTile f={{ id: 0, name: '♥ Favorites', count: c.favorites.length, covers: c.favorites.map((r) => r.image_url).filter((u): u is string => !!u).slice(0, 4) }} to="/favorites" />
              {c.folders.map((f) => <FolderTile key={f.id} f={f} />)}
              <button onClick={() => { const n = prompt('New folder name'); if (n?.trim()) newFolder.mutate(n.trim()) }}
                className="press grid aspect-square place-items-center rounded-3xl border-2 border-dashed border-stone-300 font-semibold text-stone-500 hover:text-ink">
                ＋ New folder
              </button>
            </div>
          ) : (
            <div className="mt-2">
              <FolderRow to="/favorites" name="♥ Favorites" count={c.favorites.length} cover={c.favorites.find((r) => r.image_url)?.image_url ?? null} />
              {c.folders.map((f) => <FolderRow key={f.id} to={`/folders/${f.id}`} name={f.name} count={f.count} cover={f.covers[0] ?? null} />)}
              <button onClick={() => { const n = prompt('New folder name'); if (n?.trim()) newFolder.mutate(n.trim()) }}
                className="press mt-1 w-full rounded-2xl p-3 text-left font-semibold text-stone-500 hover:bg-sand hover:text-ink">＋ New folder</button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function groupBy<T>(list: T[], key: (x: T) => string): Record<string, T[]> {
  const out: Record<string, T[]> = {}
  for (const x of list) (out[key(x)] ??= []).push(x)
  return out
}

const Stat = ({ n, label, dot }: { n?: number; label: string; dot: string }) => (
  <div>
    <p className="font-display text-2xl font-extrabold">{n ?? '–'}</p>
    <p className="flex items-center gap-1.5 text-xs text-stone-500"><span className={`h-2 w-2 rounded-full ${dot}`} />{label}</p>
  </div>
)

/** Cook Well's grid: one square per day, greener the more you cooked; red when you ate out. */
function Calendar({ days, out }: { days: Record<string, number>; out: string[] }) {
  const start = addDays(weekStart(today()), -7 * (WEEKS - 1))
  const ateOut = new Set(out)
  return (
    <div>
    <div className="flex gap-2">
      <div className="grid grid-rows-7 gap-[3px] pt-0 text-[10px] text-stone-400">
        {['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((d, i) => <span key={i} className="flex h-3.5 items-center">{d}</span>)}
      </div>
      <div className="grid flex-1 grid-flow-col grid-rows-7 gap-[3px] overflow-hidden">
        {Array.from({ length: WEEKS * 7 }, (_, i) => {
          const d = addDays(start, i)
          const n = days[d] ?? 0
          const cls = d > today() ? 'bg-transparent' : ateOut.has(d) ? 'bg-danger' : n === 0 ? 'bg-sand' : n === 1 ? 'bg-ember-bright/45' : n === 2 ? 'bg-ember-bright/70' : 'bg-ember-bright'
          return <span key={d} title={`${d}: ${plural(n, 'meal')} cooked${ateOut.has(d) ? ' · ate out' : ''}`} className={`h-3.5 w-full min-w-2 rounded-[3px] ${cls}`} />
        })}
      </div>
    </div>
    <div className="mt-3 flex gap-4 text-xs text-stone-500">
      <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-[3px] bg-ember-bright" />Cooked</span>
      <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-[3px] bg-danger" />Ate out</span>
    </div>
    </div>
  )
}

/** Recipes as photo cards (grid) or rows (list). */
function Recipes({ list, view }: { list: RecipeSummary[]; view: 'grid' | 'list' }) {
  if (view === 'list') return <div className="mt-2">{list.map((r) => <Row key={r.id} r={r} />)}</div>
  return (
    <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 lg:grid-cols-4">
      {list.map((r) => (
        <Link key={r.id} to={`/recipes/${r.id}`} className="press group">
          <div className="lift relative grid aspect-square place-items-center overflow-hidden rounded-3xl bg-sand">
            {r.image_url ? <img src={thumb(r.image_url, 480, 480)} alt="" loading="lazy" className="h-full w-full object-cover transition duration-500 group-hover:scale-105" />
              : <span className="text-5xl">🍳</span>}
            {r.kcal_per_serving ? <span className="absolute bottom-2.5 left-2.5 rounded-full bg-white/90 px-2.5 py-1 text-xs font-bold text-black">{Math.round(r.kcal_per_serving)} kcal</span> : null}
            {r.is_prep && <span className="absolute left-2.5 top-2.5 rounded-full bg-ember-bright px-2.5 py-1 text-xs font-bold text-on-go">🫙 Prep</span>}
          </div>
          <h3 className="mt-2 line-clamp-2 font-display text-lg font-bold leading-snug">{r.title}</h3>
          <p className="text-sm text-stone-500">{[r.total_minutes && `${r.total_minutes} min`, r.cuisine].filter(Boolean).join(' · ')}</p>
        </Link>
      ))}
    </div>
  )
}

function Row({ r, sub }: { r: RecipeSummary; sub?: string }) {
  return (
    <Link to={`/recipes/${r.id}`} className="press flex items-center gap-3 rounded-2xl p-2 hover:bg-sand">
      <span className="grid h-14 w-14 shrink-0 place-items-center overflow-hidden rounded-2xl bg-sand text-2xl">
        {r.image_url ? <img src={thumb(r.image_url, 160)} alt="" className="h-full w-full object-cover" /> : '🍳'}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate font-semibold">{r.title}</span>
        <span className="text-sm text-stone-500">{sub ?? [r.total_minutes && `${r.total_minutes} min`, r.kcal_per_serving && kcal(r.kcal_per_serving)].filter(Boolean).join(' · ')}</span>
      </span>
    </Link>
  )
}

/** Grid or list: saved on your account, so the app shows the same. */
function useCatalogView(): ['grid' | 'list', (v: 'grid' | 'list') => void] {
  const qc = useQueryClient()
  const view = useMe().data?.catalog_view ?? 'grid'
  const set = (v: Me['catalog_view']) => {
    qc.setQueryData<Me | null>(ME, (m) => m && { ...m, catalog_view: v })
    api.setCatalogView(v).catch(() => qc.invalidateQueries({ queryKey: ME }))
  }
  return [view, set]
}

function ViewToggle({ view, setView }: { view: 'grid' | 'list'; setView: (v: 'grid' | 'list') => void }) {
  return (
    <div className="flex rounded-full bg-sand p-1" role="group" aria-label="View">
      {(['grid', 'list'] as const).map((v) => (
        <button key={v} onClick={() => setView(v)} aria-pressed={view === v} title={v === 'grid' ? 'Grid' : 'List'}
          className={`press grid h-8 w-10 place-items-center rounded-full ${view === v ? 'bg-paper shadow-sm' : 'text-stone-500'}`}>
          <svg viewBox="0 0 24 24" className="h-5 w-5 fill-current" aria-hidden>
            <path d={v === 'grid' ? 'M3 3h8v8H3zm10 0h8v8h-8zM3 13h8v8H3zm10 0h8v8h-8z' : 'M3 5h18v2H3zm0 6h18v2H3zm0 6h18v2H3z'} />
          </svg>
        </button>
      ))}
    </div>
  )
}

function FolderRow({ to, name, count, cover }: { to: string; name: string; count: number; cover: string | null }) {
  return (
    <Link to={to} className="press flex items-center gap-3 rounded-2xl p-2 hover:bg-sand">
      <span className="grid h-14 w-14 shrink-0 place-items-center overflow-hidden rounded-2xl bg-sand text-2xl">
        {cover ? <img src={thumb(cover, 160)} alt="" className="h-full w-full object-cover" /> : '📁'}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate font-semibold">{name}</span>
        <span className="text-sm text-stone-500">{plural(count, 'recipe')}</span>
      </span>
    </Link>
  )
}

function FolderTile({ f, to }: { f: FolderSummary; to?: string }) {
  return (
    <Link to={to ?? `/folders/${f.id}`} className="press lift">
      <div className="grid aspect-square grid-cols-2 grid-rows-2 overflow-hidden rounded-3xl bg-sand">
        {f.covers.length ? f.covers.slice(0, 4).map((u) => <img key={u} src={thumb(u, 240)} alt="" className="h-full w-full object-cover" />)
          : <span className="col-span-2 row-span-2 grid place-items-center text-5xl">📁</span>}
      </div>
      <p className="mt-2 truncate font-display text-lg font-bold">{f.name}</p>
      <p className="text-sm text-stone-500">{plural(f.count, 'recipe')}</p>
    </Link>
  )
}

/** One folder (or your favorites, at /favorites): its recipes, in grid or list. */
export function Folder() {
  const param = useParams().id
  const favorites = param === undefined
  const id = Number(param)
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [view, setView] = useCatalogView()
  const folder = useQuery({ queryKey: ['folder', id], queryFn: () => api.folder(id), enabled: !favorites })
  const catalog = useQuery({ queryKey: ['catalog'], queryFn: api.catalog, enabled: favorites })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['folder', id] }); qc.invalidateQueries({ queryKey: ['catalog'] }) }
  const f = favorites ? (catalog.data && { id: 0, name: '♥ Favorites', recipes: catalog.data.favorites }) : folder.data
  const remove = async (recipeId: number) => {
    if (favorites) await api.setFavorite(recipeId, false)
    else await api.setInFolder(id, recipeId, false)
    refresh()
  }
  return (
    <div className="rise mx-auto max-w-4xl">
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <button onClick={() => navigate(-1)} aria-label="Back" className="press grid h-11 w-11 place-items-center rounded-full bg-paper ring-1 ring-stone-200">←</button>
        <div className="flex-1">
          <p className="text-sm text-stone-500">{f ? plural(f.recipes.length, 'recipe') : ''}</p>
          <h1 className="font-display text-4xl font-extrabold">{f?.name ?? (favorites ? 'Favorites' : 'Folder')}</h1>
        </div>
        <ViewToggle view={view} setView={setView} />
        {!favorites && <>
          <Button variant="ghost" onClick={async () => { const n = prompt('Rename folder', f?.name); if (n?.trim()) { await api.renameFolder(id, n.trim()); refresh() } }}>Rename</Button>
          <Button variant="ghost" onClick={async () => {
            if (confirm(`Delete “${f?.name}”? Only the folder goes; its recipes stay.`)) { await api.deleteFolder(id); refresh(); navigate('/profile') }
          }}>Delete</Button>
        </>}
      </div>
      {f?.recipes.length === 0 && (favorites
        ? <Empty emoji="♡" title="No favorites yet" body="Click the heart on any recipe to keep it here." />
        : <Empty emoji="📁" title="This folder is empty" body="Open a recipe and click the bookmark to add it here." />)}
      {f && view === 'grid' ? <Recipes list={f.recipes} view="grid" /> : f?.recipes.map((r) => (
        <div key={r.id} className="flex items-center">
          <div className="flex-1"><Row r={r} /></div>
          <button className="px-3 text-sm font-semibold text-stone-500 hover:text-ink" onClick={() => remove(r.id)}>{favorites ? 'Unfavorite' : 'Remove'}</button>
        </div>
      ))}
    </div>
  )
}
