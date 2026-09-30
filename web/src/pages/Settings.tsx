import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { api, type Me } from '../api'
import { ME, useDeleteAccount, useMe, useSignOut } from '../auth'
import { FeedbackForm, FeedbackInbox } from '../components/Feedback'
import { Button, PageHeader, SectionTitle } from '../components/ui'
import { GoalDialog } from './Home'
import { LegalLinks } from './Legal'

const COFFEE_URL = 'https://buymeacoffee.com/devaa'
const SOURCE_URL = 'https://github.com/devasenan134/cauldron'

export default function Settings() {
  const me = useMe().data
  const signOut = useSignOut()
  const app = useQuery({ queryKey: ['app-latest'], queryFn: api.appLatest })
  const [editingGoal, setEditingGoal] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const goal = me?.kcal_goal ?? 2200
  const qc = useQueryClient()
  const setTheme = useMutation({
    mutationFn: api.setTheme,
    // Switch at once; the server's answer follows.
    onMutate: (theme) => qc.setQueryData<Me | null>(ME, (m) => m && { ...m, theme }),
    onSuccess: (m) => qc.setQueryData(ME, m),
  })

  return (
    <div className="rise mx-auto max-w-2xl">
      <PageHeader title="Settings" />

      <SectionTitle>Account</SectionTitle>
      <Card>
        <div className="flex items-center gap-4">
          <div className="grid h-14 w-14 place-items-center rounded-full bg-sand font-display text-2xl font-bold">
            {(me?.name || me?.email || '?').charAt(0).toUpperCase()}
          </div>
          <div className="min-w-0 flex-1">
            <p className="font-semibold">{me?.name || 'Signed in'}</p>
            <p className="truncate text-sm text-stone-500">{me?.email}</p>
            {me?.is_owner && <p className="text-sm font-semibold text-ember">Owner</p>}
          </div>
          <Button variant="ghost" onClick={() => signOut.mutate()}>Sign out</Button>
        </div>
        <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-stone-200 pt-4">
          {/* A plain link: the browser downloads the file, with the session cookie. */}
          <a href="/api/auth/me/export" download><Button variant="ghost">Download my data</Button></a>
          {!me?.is_owner && <Button variant="ghost" className="!text-danger" onClick={() => setDeleting(true)}>Delete my account</Button>}
        </div>
        <p className="mt-2 text-sm text-stone-500">
          {me?.is_owner ? 'A JSON file with all your recipes, plans, lists and foods.'
            : 'A JSON file with all your recipes, plans, lists and foods. Deleting your account removes all of it.'}
        </p>
      </Card>

      <SectionTitle>Appearance</SectionTitle>
      <Card>
        <p className="font-semibold">Theme</p>
        <p className="text-sm text-stone-500">Saved to your account, so the app follows too</p>
        <div className="mt-3 flex rounded-full bg-sand p-1">
          {(['system', 'light', 'dark'] as const).map((t) => (
            <button key={t} onClick={() => setTheme.mutate(t)}
              className={`press flex-1 rounded-full py-2.5 text-sm capitalize transition-colors ${me?.theme === t ? 'bg-paper font-bold shadow-sm' : 'font-medium text-stone-500'}`}>
              {t}
            </button>
          ))}
        </div>
      </Card>

      <SectionTitle>Goals</SectionTitle>
      <Card>
        <button onClick={() => setEditingGoal(true)} className="press flex w-full items-center text-left">
          <div className="flex-1">
            <p className="font-semibold">Daily calorie goal</p>
            <p className="text-sm text-stone-500">Shown on Home, here and in the app</p>
          </div>
          <span className="font-display text-xl font-bold text-ember">{goal.toLocaleString()} kcal</span>
        </button>
      </Card>

      <SectionTitle>Android app</SectionTitle>
      <Card>
        {app.data ? (<>
          <div className="flex flex-wrap items-center gap-4">
            <div className="text-4xl">📱</div>
            <div className="min-w-0 flex-1">
              <p className="font-semibold">Cauldron for Android · version {app.data.version}</p>
              <p className="text-sm text-stone-500">
                {(app.data.size / 1_048_576).toFixed(1)} MB · open this page on your phone to install it. Once it's installed, it keeps itself up to date.
              </p>
            </div>
            <a href={`/api/app/download/${app.data.version}`} download><Button variant="accent">Download</Button></a>
          </div>
          {app.data.notes && (
            <div className="mt-4 border-t border-stone-200 pt-4">
              <p className="font-semibold">What's new in {app.data.version}</p>
              <PatchNotes notes={app.data.notes} />
            </div>
          )}
        </>) : (
          <p className="text-stone-500">{app.isPending ? 'Checking…' : 'No app build is available yet.'}</p>
        )}
      </Card>

      {me?.is_owner && (<>
        <SectionTitle>Recipe libraries</SectionTitle>
        <Card>
          <Link to="/settings/libraries" className="press flex w-full items-center gap-4 text-left">
            <div className="text-3xl">📚</div>
            <div className="min-w-0 flex-1">
              <p className="font-semibold">Manage the recipe libraries</p>
              <p className="text-sm text-stone-500">The recipes everyone gets, and the ones for your guests: edit, hide or add your own</p>
            </div>
            <span className="text-xl text-stone-400">›</span>
          </Link>
        </Card>
      </>)}

      <SectionTitle>Feedback</SectionTitle>
      <Card><FeedbackForm owner={!!me?.is_owner} /></Card>
      {me?.is_owner && (<>
        <SectionTitle>Everyone's feedback</SectionTitle>
        <Card><FeedbackInbox /></Card>
      </>)}

      <SectionTitle>About</SectionTitle>
      <Card>
        <p className="font-semibold">Cauldron</p>
        <p className="text-sm text-stone-500">Recipes, meal plans, batch cooking and groceries.</p>
        <p className="mt-2 text-sm text-stone-500">
          © 2026 Devasenan Murugan. Open source under the Apache License 2.0, and free to host yourself.
        </p>
        <div className="mt-4 flex flex-wrap gap-2">
          <a href={COFFEE_URL} target="_blank" rel="noreferrer"><Button variant="accent">☕ Buy me a coffee</Button></a>
          <a href={SOURCE_URL} target="_blank" rel="noreferrer"><Button variant="ghost">Source code</Button></a>
        </div>
        <LegalLinks className="mt-4" />
      </Card>

      {editingGoal && <GoalDialog goal={goal} onClose={() => setEditingGoal(false)} />}
      {deleting && <DeleteAccountDialog onClose={() => setDeleting(false)} />}
    </div>
  )
}

/** Deleting your account: you type DELETE to confirm. */
function DeleteAccountDialog({ onClose }: { onClose: () => void }) {
  const [text, setText] = useState('')
  const del = useDeleteAccount()
  const ready = text.trim() === 'DELETE'
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" onClick={onClose}>
      <div className="rise w-full max-w-sm rounded-3xl bg-cream p-6 shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <h3 className="font-display text-2xl font-bold">Delete your account?</h3>
        <p className="mt-2 text-sm text-stone-500">
          This deletes your recipes and their photos, your plans, meal log, grocery lists, foods and folders, on the
          website and in the app. It can't be undone. Download your data first if you want a copy.
        </p>
        <p className="mt-4 text-sm font-semibold">Type DELETE to confirm</p>
        <input autoFocus value={text} onChange={(e) => setText(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && ready && del.mutate()}
          autoCapitalize="characters" autoComplete="off" spellCheck={false}
          className="mt-2 w-full rounded-2xl bg-paper px-4 py-3 font-semibold outline-none ring-1 ring-stone-200 focus:ring-2 focus:ring-danger/50" />
        {del.isError && <p className="mt-3 text-sm text-danger">{reason(del.error)}</p>}
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={!ready || del.isPending} onClick={() => del.mutate()} className="!bg-danger !text-white">
            {del.isPending ? 'Deleting…' : 'Delete everything'}
          </Button>
        </div>
      </div>
    </div>
  )
}

/** The server's reason ("An import is still running…"), from the error's JSON body. */
function reason(e: Error): string {
  return e.message.match(/"detail":\s*"([^"]*)"/)?.[1] ?? "Couldn't delete your account. Please try again."
}

/** Release notes as bullets; long ones fold to a few lines with "See more". */
function PatchNotes({ notes, foldAt = 4 }: { notes: string; foldAt?: number }) {
  const [open, setOpen] = useState(false)
  const lines = notes.split('\n').filter((l) => l.trim()).map((l) => l.replace(/^\s*[-*]\s+/, ''))
  const long = lines.length > foldAt || notes.length > 280
  return (
    <div className="mt-1">
      <ul className={`list-disc space-y-1 pl-5 text-stone-700 ${long && !open ? 'line-clamp-4' : ''}`}>
        {lines.map((l, i) => <li key={i}>{l}</li>)}
      </ul>
      {long && <button onClick={() => setOpen(!open)} className="mt-1 font-semibold text-ember hover:underline">{open ? 'See less' : 'See more'}</button>}
    </div>
  )
}

const Card = ({ children }: { children: ReactNode }) => <div className="rounded-3xl bg-paper p-5 ring-1 ring-stone-200">{children}</div>
