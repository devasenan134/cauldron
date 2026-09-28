import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { api, type Me } from '../api'
import { ME, useMe, useSignOut } from '../auth'
import { Button, PageHeader, SectionTitle } from '../components/ui'
import { GoalDialog } from './Home'

export default function Settings() {
  const me = useMe().data
  const signOut = useSignOut()
  const app = useQuery({ queryKey: ['app-latest'], queryFn: api.appLatest })
  const [editingGoal, setEditingGoal] = useState(false)
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

      <SectionTitle>About</SectionTitle>
      <Card>
        <p className="font-semibold">Cauldron</p>
        <p className="text-sm text-stone-500">Recipes, meal plans, batch cooking and groceries.</p>
        <p className="mt-2 text-sm text-stone-500">© 2026 Devasenan Murugan. All rights reserved.</p>
      </Card>

      {editingGoal && <GoalDialog goal={goal} onClose={() => setEditingGoal(false)} />}
    </div>
  )
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
