import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useBlocker, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api, LIBRARY_SOURCES, type RecipeDetail, type RecipeIn } from '../api'
import { Button, Chip } from '../components/ui'
import { thumb } from '../format'
import { refreshPlan } from '../plan'

// The editor's working copy. Ingredients sit in sections ("Sauce", "Toppings"), like the library's.
// prep_id: made from one of your prepped ingredients (dropped when the name changes; the server
// links a name that matches a prep by itself).
type Row = { name: string; label: string; note: string; prep_id: number | null }
type Section = { name: string; rows: Row[] }
type StepRow = { title: string; text: string }
type Draft = {
  title: string; description: string; image_url: string | null; minutes: string; servings: string; yield_text: string
  cuisine: string; category: string; tags: string[]; video_url: string; source_url: string; notes: string
  is_prep: boolean; yield_grams: string
  sections: Section[]; steps: StepRow[]
}

const blankRow = (): Row => ({ name: '', label: '', note: '', prep_id: null })
const empty = (): Draft => ({
  title: '', description: '', image_url: null, minutes: '', servings: '', yield_text: '', cuisine: '', category: '', tags: [],
  video_url: '', source_url: '', notes: '', is_prep: false, yield_grams: '', sections: [{ name: '', rows: [blankRow()] }], steps: [{ title: '', text: '' }],
})

function fromRecipe(r: RecipeDetail): Draft {
  const groups = new Map<string, Row[]>()
  for (const i of r.ingredients) groups.set(i.group ?? '', [...(groups.get(i.group ?? '') ?? []), { name: i.name, label: i.label, note: i.note, prep_id: i.prep_id }])
  return {
    title: r.title, description: r.description, image_url: r.image_url, minutes: r.total_minutes?.toString() ?? '',
    servings: r.servings?.toString() ?? '', yield_text: r.yield_text ?? '', cuisine: r.cuisine ?? '', category: r.category ?? '',
    tags: r.tags, video_url: r.video_url ?? '', source_url: r.source_url ?? '', notes: r.notes,
    is_prep: r.is_prep, yield_grams: r.yield_grams?.toString() ?? '',
    sections: groups.size ? [...groups].map(([name, rows]) => ({ name, rows })) : [{ name: '', rows: [blankRow()] }],
    steps: r.steps.length ? r.steps.map((s) => ({ title: s.title, text: s.text })) : [{ title: '', text: '' }],
  }
}

const toBody = (d: Draft): RecipeIn => ({
  title: d.title.trim(), description: d.description.trim(), image_url: d.image_url,
  video_url: d.video_url.trim() || null, source_url: d.source_url.trim() || null,
  servings: d.servings ? Number(d.servings) : null, yield_text: d.yield_text.trim() || null,
  total_minutes: d.minutes ? Number(d.minutes) : null, cuisine: d.cuisine.trim() || null, category: d.category || null,
  tags: d.tags, notes: d.notes.trim(), is_prep: d.is_prep, yield_grams: d.is_prep && Number(d.yield_grams) > 0 ? Number(d.yield_grams) : null,
  ingredients: d.sections.flatMap((s) => s.rows.filter((r) => r.name.trim()).map((r) => ({ group: s.name.trim() || null, name: r.name.trim(), note: r.note.trim(), label: r.label.trim(), prep_id: r.prep_id }))),
  steps: d.steps.filter((s) => s.text.trim()).map((s) => ({ title: s.title.trim(), text: s.text.trim() })),
})

/** A photo shrunk to at most 1600 px, as JPEG (phones and cameras take huge ones). */
async function shrink(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  return new Promise((ok, fail) => canvas.toBlob((b) => (b ? ok(b) : fail(new Error("Couldn't read that photo"))), 'image/jpeg', 0.85))
}

export default function RecipeEditor() {
  const params = useParams()
  const id = params.id ? Number(params.id) : null
  // "Make my version": a new recipe that starts as a copy of this one (made on save).
  const from = useSearchParams()[0].get('from')
  const fromId = id == null && from ? Number(from) : null
  const navigate = useNavigate()
  const qc = useQueryClient()
  const facets = useQuery({ queryKey: ['facets'], queryFn: api.facets })
  const preps = useQuery({ queryKey: ['preps'], queryFn: api.preps })
  const prepNamed = (name: string) => preps.data?.find((p) => p.id !== id && p.title.trim().toLowerCase().replace(/s$/, '') === name.trim().toLowerCase().replace(/s$/, ''))
  const source = id ?? fromId
  const existing = useQuery({ queryKey: ['recipe', source], queryFn: () => api.recipe(source!), enabled: source != null })
  const [d, setD] = useState<Draft | null>(source == null ? empty() : null)
  const [original, setOriginal] = useState<string>(source == null ? JSON.stringify(toBody(empty())) : '')
  // Fill the form once the recipe arrives (during render, not in an effect).
  if (existing.data && d == null) {
    const draft = fromRecipe(existing.data)
    if (fromId != null) draft.title = `${existing.data.title} (my version)`
    setOriginal(JSON.stringify(toBody(draft)))
    setD(draft)
  }
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const file = useRef<HTMLInputElement>(null)

  // Leaving with unsaved changes asks first (in the app and on closing the tab).
  const [saved, setSaved] = useState(false)
  const dirty = d != null && !saved && JSON.stringify(toBody(d)) !== original
  // Set just before leaving on purpose (after Save or Delete), so the guard lets that one through.
  const leaving = useRef(false)
  const blocker = useBlocker(({ currentLocation, nextLocation }) => dirty && !leaving.current && currentLocation.pathname !== nextLocation.pathname)
  useEffect(() => {
    if (blocker.state === 'blocked') {
      if (confirm(id == null ? 'Discard this recipe?' : 'Discard your changes?')) blocker.proceed()
      else blocker.reset()
    }
  }, [blocker, id])
  useEffect(() => {
    const warn = (e: BeforeUnloadEvent) => { if (dirty) e.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  if (existing.isError) return <p className="text-danger">Couldn't load the recipe: {String(existing.error)}</p>
  if (!d) return <p className="text-stone-500">Loading…</p>
  const set = (patch: Partial<Draft>) => setD({ ...d, ...patch })
  const setSection = (i: number, s: Section) => set({ sections: d.sections.map((x, j) => (j === i ? s : x)) })

  const save = async () => {
    if (!d.title.trim()) { setError('Give your recipe a title'); return }
    setSaving(true); setError(null)
    try {
      const r = fromId != null ? await api.recipe((await api.makeVariation(fromId, toBody(d))).id)
        : id == null ? await api.createRecipe(toBody(d)) : await api.updateRecipe(id, toBody(d))
      if (fromId != null) { qc.invalidateQueries({ queryKey: ['recipe', fromId] }); qc.invalidateQueries({ queryKey: ['catalog'] }) }
      qc.setQueryData(['recipe', r.id], r)
      qc.invalidateQueries({ queryKey: ['recipes'] })
      refreshPlan(qc)
      setSaved(true)
      leaving.current = true
      navigate(`/recipes/${r.id}`, { replace: id == null })
    } catch (e) { setError(String(e)) } finally { setSaving(false) }
  }
  const remove = async () => {
    if (id == null || !confirm(`Delete “${d.title}”? It will also leave your plan.`)) return
    await api.deleteRecipe(id)
    qc.removeQueries({ queryKey: ['recipe', id] })
    qc.invalidateQueries({ queryKey: ['recipes'] })
    refreshPlan(qc)
    setSaved(true)
    leaving.current = true
    navigate('/recipes?mine=1', { replace: true })
  }
  const pick = async (f: File | undefined) => {
    if (!f) return
    setUploading(true); setError(null)
    try { set({ image_url: await api.uploadImage(await shrink(f)) }) } catch (e) { setError(String(e)) } finally { setUploading(false) }
  }

  const meals = [...new Set([...(facets.data?.categories ?? []), 'Breakfast', 'Lunch', 'Dinner', 'Side', 'Snack', 'Dessert'])]

  return (
    <div className="rise mx-auto max-w-3xl">
      <div className="sticky top-16 z-20 -mx-5 mb-4 flex items-center gap-3 bg-cream/90 px-5 py-3 backdrop-blur-md">
        <button onClick={() => navigate(-1)} className="press grid h-11 w-11 place-items-center rounded-full bg-paper text-lg shadow-[0_4px_16px_rgba(0,0,0,0.08)] ring-1 ring-stone-200" aria-label="Back">←</button>
        <h1 className="flex-1 font-display text-3xl font-extrabold">{fromId != null ? 'My version' : id == null ? 'New recipe' : 'Edit recipe'}</h1>
        <Button variant="accent" onClick={save} disabled={saving || uploading}>{saving ? 'Saving…' : 'Save'}</Button>
      </div>
      {error && <p className="mb-4 rounded-2xl bg-pink-soft p-3 text-sm font-semibold text-pink-deep">{error}</p>}

      <button onClick={() => file.current?.click()}
        className="press relative grid aspect-[4/3] w-full place-items-center overflow-hidden rounded-3xl bg-sand sm:aspect-[16/9]">
        {d.image_url && <img src={thumb(d.image_url, 1200, 800)} alt="" className="absolute inset-0 h-full w-full object-cover" />}
        {uploading ? <span className="relative font-semibold text-stone-500">Uploading…</span>
          : d.image_url ? <span className="absolute bottom-3 right-3 rounded-full bg-black/55 px-3 py-1.5 text-sm font-semibold text-white">Change photo</span>
          : <span className="text-center font-semibold text-stone-500"><span className="block text-4xl">📷</span>Add a photo</span>}
      </button>
      <input ref={file} type="file" accept="image/*" hidden onChange={(e) => pick(e.target.files?.[0])} />

      <Field value={d.title} onChange={(v) => set({ title: v })} placeholder="Recipe title" big />
      <Field value={d.description} onChange={(v) => set({ description: v })} placeholder="A line about it (what makes it good?)" multiline />
      <div className="grid grid-cols-2 gap-3">
        <Field label="Time" value={d.minutes} onChange={(v) => set({ minutes: v.replace(/\D/g, '').slice(0, 4) })} placeholder="Minutes" />
        <Field label="Makes" value={d.servings} onChange={(v) => set({ servings: v.replace(/[^\d.]/g, '').slice(0, 4) })} placeholder="Servings" />
      </div>
      <Field value={d.yield_text} onChange={(v) => set({ yield_text: v })} placeholder="Yield as you'd say it, e.g. 3-4 burritos (optional)" />
      <div className={`mt-3 rounded-2xl p-4 ring-1 ${d.is_prep ? 'bg-ember-soft ring-transparent' : 'bg-paper ring-stone-200'}`}>
        <label className="flex cursor-pointer items-center gap-3">
          <input type="checkbox" checked={d.is_prep} onChange={(e) => set({ is_prep: e.target.checked })} className="h-5 w-5 accent-ember-bright" />
          <span className="flex-1">
            <span className="block font-semibold">🫙 Prepped ingredient</span>
            <span className="block text-sm text-stone-500">Cooked rice, pickled onions, a sauce: other recipes use it by weight, and what you make goes in the fridge.</span>
          </span>
        </label>
        {d.is_prep && (
          <label className="mt-3 flex items-center gap-2 text-sm">
            <span className="font-semibold">Weighs when done</span>
            <input value={d.yield_grams} inputMode="decimal" onChange={(e) => set({ yield_grams: e.target.value.replace(/[^\d.]/g, '').slice(0, 6) })}
              placeholder="auto" className={`${small} w-24 text-right`} />
            <span>g</span>
            <span className="text-xs text-stone-500">blank: what the ingredients weigh</span>
          </label>
        )}
      </div>
      <Group title="Meal">{meals.map((c) => <Chip key={c} selected={d.category === c} onClick={() => set({ category: d.category === c ? '' : c })}>{c}</Chip>)}</Group>
      <Field label="Cuisine" value={d.cuisine} onChange={(v) => set({ cuisine: v })} placeholder="Cuisine, e.g. Indian" list="cuisines" />
      <datalist id="cuisines">{facets.data?.cuisines.map((c) => <option key={c} value={c} />)}</datalist>
      {facets.data?.tag_groups.filter((g) => g.name !== 'More').map((g) => (
        <Group key={g.name} title={g.name}>
          {g.tags.map((t) => <Chip key={t} selected={d.tags.includes(t)} onClick={() => set({ tags: d.tags.includes(t) ? d.tags.filter((x) => x !== t) : [...d.tags, t] })}>{t}</Chip>)}
        </Group>
      ))}

      <h2 className="mt-10 font-display text-3xl font-bold">Ingredients</h2>
      <p className="mb-3 text-sm text-stone-500">Write amounts the way you'd say them: 200 g, 2 cloves, 1 tbsp, a drizzle. Calories are worked out when you save. Name one of your prepped ingredients (e.g. 300 g cooked rice) to use it.</p>
      <datalist id="preps">{preps.data?.filter((p) => p.id !== id).map((p) => <option key={p.id} value={p.title} />)}</datalist>
      {d.sections.map((s, si) => (
        <div key={si} className="mb-3 rounded-3xl bg-paper p-4 ring-1 ring-stone-200">
          <div className="flex items-center gap-2">
            <input value={s.name} onChange={(e) => setSection(si, { ...s, name: e.target.value })}
              placeholder={d.sections.length > 1 ? 'Section name, e.g. Sauce' : 'Section (optional), e.g. Sauce'} className={`${small} flex-1 font-semibold`} />
            {d.sections.length > 1 && <IconBtn label="Remove section" onClick={() => set({ sections: d.sections.filter((_, j) => j !== si) })}>🗑</IconBtn>}
          </div>
          {s.rows.map((r, ri) => {
            const setRow = (patch: Partial<Row>) => setSection(si, { ...s, rows: s.rows.map((x, j) => (j === ri ? { ...x, ...patch } : x)) })
            return (
              <div key={ri} className="mt-2 flex items-start gap-2">
                <input value={r.label} onChange={(e) => setRow({ label: e.target.value })} placeholder="Amount" className={`${small} w-28`} />
                <div className="flex flex-1 flex-col gap-1.5 sm:flex-row">
                  <div className="relative flex flex-1">
                    <input value={r.name} onChange={(e) => setRow({ name: e.target.value, prep_id: null })} placeholder="Ingredient" list="preps"
                      className={`${small} flex-1 ${r.prep_id || prepNamed(r.name) ? 'pr-16' : ''}`} />
                    {(r.prep_id || prepNamed(r.name)) && (
                      <span title="Uses your prepped ingredient" className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 rounded-full bg-ember-bright px-2 py-0.5 text-[11px] font-bold text-on-go">🫙 prep</span>
                    )}
                  </div>
                  <input value={r.note} onChange={(e) => setRow({ note: e.target.value })} placeholder="Note, e.g. diced (optional)" className={`${small} flex-1`} />
                </div>
                <IconBtn label="Remove ingredient" onClick={() => setSection(si, { ...s, rows: s.rows.length > 1 ? s.rows.filter((_, j) => j !== ri) : [blankRow()] })}>✕</IconBtn>
              </div>
            )
          })}
          <button onClick={() => setSection(si, { ...s, rows: [...s.rows, blankRow()] })} className="mt-3 font-semibold hover:underline">＋ Add ingredient</button>
        </div>
      ))}
      <button onClick={() => set({ sections: [...d.sections, { name: '', rows: [blankRow()] }] })} className="font-semibold text-stone-500 hover:text-ink">＋ Add a section</button>

      <h2 className="mb-3 mt-10 font-display text-3xl font-bold">Steps</h2>
      {d.steps.map((s, i) => {
        const setStep = (patch: Partial<StepRow>) => set({ steps: d.steps.map((x, j) => (j === i ? { ...x, ...patch } : x)) })
        return (
          <div key={i} className="mb-3 flex gap-3">
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-ink font-display font-bold text-cream">{i + 1}</span>
            <div className="flex flex-1 flex-col gap-2 rounded-3xl bg-paper p-3 ring-1 ring-stone-200">
              <input value={s.title} onChange={(e) => setStep({ title: e.target.value })} placeholder="Step title, e.g. Sear the chicken (optional)" className={`${small} font-semibold`} />
              <textarea value={s.text} onChange={(e) => setStep({ text: e.target.value })} placeholder="What to do" rows={3} className={`${small} resize-y`} />
            </div>
            <IconBtn label="Remove step" onClick={() => set({ steps: d.steps.length > 1 ? d.steps.filter((_, j) => j !== i) : [{ title: '', text: '' }] })}>✕</IconBtn>
          </div>
        )
      })}
      <button onClick={() => set({ steps: [...d.steps, { title: '', text: '' }] })} className="font-semibold hover:underline">＋ Add a step</button>

      <h2 className="mt-10 font-display text-3xl font-bold">More</h2>
      <Field label="Video" value={d.video_url} onChange={(v) => set({ video_url: v })} placeholder="Video link (YouTube, Reels…)" />
      <Field label="Source" value={d.source_url} onChange={(v) => set({ source_url: v })} placeholder="Where it's from (a link, optional)" />
      <Field label="Notes" value={d.notes} onChange={(v) => set({ notes: v })} placeholder="Notes for next time" multiline />
      {/* Library recipes aren't deleted, only hidden (Settings → Recipe libraries). */}
      {id != null && !LIBRARY_SOURCES.includes(existing.data?.source ?? '') && <button onClick={remove} className="mt-8 font-semibold text-danger hover:underline">🗑 Delete this recipe</button>}
      <div className="h-16" />
    </div>
  )
}

const small = 'rounded-xl bg-cream px-3 py-2.5 outline-none ring-1 ring-stone-200 placeholder:text-stone-400 focus:ring-2 focus:ring-ember-bright/50'

function Field({ value, onChange, placeholder, label, big, multiline, list }: {
  value: string; onChange: (v: string) => void; placeholder: string; label?: string; big?: boolean; multiline?: boolean; list?: string
}) {
  const cls = `w-full rounded-2xl bg-paper px-4 py-3.5 outline-none ring-1 ring-stone-200 placeholder:text-stone-400 focus:ring-2 focus:ring-ember-bright/50 ${big ? 'font-display text-2xl font-bold' : ''}`
  return (
    <label className="mt-3 block">
      {label && <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-stone-500">{label}</span>}
      {multiline ? <textarea value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} rows={2} className={cls} />
        : <input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} list={list} className={cls} />}
    </label>
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

const IconBtn = ({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) => (
  <button aria-label={label} title={label} onClick={onClick} className="press grid h-10 w-10 shrink-0 place-items-center rounded-full text-stone-400 hover:bg-sand hover:text-ink">{children}</button>
)
