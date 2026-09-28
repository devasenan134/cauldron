import { useQuery } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { api } from '../api'
import { useMe, useSignOut } from '../auth'
import { Button, PageHeader, SectionTitle } from '../components/ui'
import { GoalDialog } from './Home'

export default function Settings() {
  const me = useMe().data
  const signOut = useSignOut()
  const app = useQuery({ queryKey: ['app-latest'], queryFn: api.appLatest })
  const [editingGoal, setEditingGoal] = useState(false)
  const goal = me?.kcal_goal ?? 2200

  return (
    <div className="rise mx-auto max-w-2xl">
      <PageHeader title="Settings" />

      <SectionTitle>Account</SectionTitle>
      <Card>
        <div className="flex items-center gap-4">
          <div className="grid h-14 w-14 place-items-center rounded-full bg-ink font-display text-2xl font-bold text-cream">
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
        {app.data ? (
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
        ) : (
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

const Card = ({ children }: { children: ReactNode }) => <div className="rounded-3xl bg-paper p-5">{children}</div>
