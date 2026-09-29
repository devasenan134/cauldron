import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, type FolderSummary, type RecipeSummary } from '../api'
import { useMe } from '../auth'
import { Button, Chip, Empty, Shimmer } from '../components/ui'
import { addDays, dayLabel, today, weekdayLong, weekStart } from '../dates'
import { kcal, num, plural, thumb } from '../format'

const WEEKS = 20

/** Your profile, like Cook Well's: who you are, how you've been cooking, then Cooked and Catalog. */
export default function Profile() {
  const me = useMe().data
  const navigate = useNavigate()
  const qc = useQueryClient()
  const profile = useQuery({ queryKey: ['profile'], queryFn: api.profile })
  const cooked = useQuery({ queryKey: ['cooked'], queryFn: api.cooked })
  const catalog = useQuery({ queryKey: ['catalog'], queryFn: api.catalog })
  const [tab, setTab] = useState<'cooked' | 'catalog'>('cooked')
  const [shelf, setShelf] = useState<'mine' | 'favorites' | 'folders'>('mine')
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
        <div className="rounded-3xl p-5 ring-1 ring-stone-200">{p ? <Calendar days={p.days} /> : <Shimmer className="h-40 rounded-2xl" />}</div>
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
        {(['cooked', 'catalog'] as const).map((t) => (
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
          <div className="flex flex-wrap gap-2">
            <Chip selected={shelf === 'mine'} onClick={() => setShelf('mine')}>My recipes{c ? ` · ${c.mine.length}` : ''}</Chip>
            <Chip selected={shelf === 'favorites'} onClick={() => setShelf('favorites')}>Favorites{c ? ` · ${c.favorites.length}` : ''}</Chip>
            <Chip selected={shelf === 'folders'} onClick={() => setShelf('folders')}>Folders{c ? ` · ${c.folders.length}` : ''}</Chip>
          </div>
          {!c ? <Shimmer className="mt-4 h-20 rounded-2xl" /> : shelf === 'mine' ? (
            c.mine.length ? c.mine.map((r) => <Row key={r.id} r={r} />)
              : <Empty emoji="🧑‍🍳" title="No recipes of your own yet" body="Write one, or open any recipe and click “Make my version”."
                  action={<Button onClick={() => navigate('/recipes/new')}>＋ New recipe</Button>} />
          ) : shelf === 'favorites' ? (
            c.favorites.length ? c.favorites.map((r) => <Row key={r.id} r={r} />)
              : <Empty emoji="♡" title="No favorites yet" body="Click the heart on any recipe to keep it here." />
          ) : (
            <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
              <button onClick={() => { const n = prompt('New folder name'); if (n?.trim()) newFolder.mutate(n.trim()) }}
                className="press grid aspect-square place-items-center rounded-3xl border-2 border-dashed border-stone-300 font-semibold text-stone-500 hover:text-ink">
                ＋ New folder
              </button>
              {c.folders.map((f) => <FolderTile key={f.id} f={f} />)}
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

/** Cook Well's grid: one square per day, greener the more you cooked. */
function Calendar({ days }: { days: Record<string, number> }) {
  const start = addDays(weekStart(today()), -7 * (WEEKS - 1))
  return (
    <div className="flex gap-2">
      <div className="grid grid-rows-7 gap-[3px] pt-0 text-[10px] text-stone-400">
        {['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((d, i) => <span key={i} className="flex h-3.5 items-center">{d}</span>)}
      </div>
      <div className="grid flex-1 grid-flow-col grid-rows-7 gap-[3px] overflow-hidden">
        {Array.from({ length: WEEKS * 7 }, (_, i) => {
          const d = addDays(start, i)
          const n = days[d] ?? 0
          const cls = d > today() ? 'bg-transparent' : n === 0 ? 'bg-sand' : n === 1 ? 'bg-ember-bright/45' : n === 2 ? 'bg-ember-bright/70' : 'bg-ember-bright'
          return <span key={d} title={`${d}: ${n}`} className={`h-3.5 w-full min-w-2 rounded-[3px] ${cls}`} />
        })}
      </div>
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

function FolderTile({ f }: { f: FolderSummary }) {
  return (
    <Link to={`/folders/${f.id}`} className="press lift">
      <div className="grid aspect-square grid-cols-2 grid-rows-2 overflow-hidden rounded-3xl bg-sand">
        {f.covers.length ? f.covers.slice(0, 4).map((u) => <img key={u} src={thumb(u, 240)} alt="" className="h-full w-full object-cover" />)
          : <span className="col-span-2 row-span-2 grid place-items-center text-5xl">📁</span>}
      </div>
      <p className="mt-2 truncate font-display text-lg font-bold">{f.name}</p>
      <p className="text-sm text-stone-500">{plural(f.count, 'recipe')}</p>
    </Link>
  )
}

/** One folder: its recipes, with rename and delete. */
export function Folder() {
  const id = Number(useParams().id)
  const qc = useQueryClient()
  const navigate = useNavigate()
  const folder = useQuery({ queryKey: ['folder', id], queryFn: () => api.folder(id) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['folder', id] }); qc.invalidateQueries({ queryKey: ['catalog'] }) }
  const f = folder.data
  return (
    <div className="rise mx-auto max-w-3xl">
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <button onClick={() => navigate(-1)} aria-label="Back" className="press grid h-11 w-11 place-items-center rounded-full bg-paper ring-1 ring-stone-200">←</button>
        <div className="flex-1">
          <p className="text-sm text-stone-500">{f ? plural(f.recipes.length, 'recipe') : ''}</p>
          <h1 className="font-display text-4xl font-extrabold">{f?.name ?? 'Folder'}</h1>
        </div>
        <Button variant="ghost" onClick={async () => { const n = prompt('Rename folder', f?.name); if (n?.trim()) { await api.renameFolder(id, n.trim()); refresh() } }}>Rename</Button>
        <Button variant="ghost" onClick={async () => {
          if (confirm(`Delete “${f?.name}”? Only the folder goes; its recipes stay.`)) { await api.deleteFolder(id); refresh(); navigate('/profile') }
        }}>Delete</Button>
      </div>
      {f?.recipes.length === 0 && <Empty emoji="📁" title="This folder is empty" body="Open a recipe and click the bookmark to add it here." />}
      {f?.recipes.map((r) => (
        <div key={r.id} className="flex items-center">
          <div className="flex-1"><Row r={r} /></div>
          <button className="px-3 text-sm font-semibold text-stone-500 hover:text-ink" onClick={async () => { await api.setInFolder(id, r.id, false); refresh() }}>Remove</button>
        </div>
      ))}
    </div>
  )
}
