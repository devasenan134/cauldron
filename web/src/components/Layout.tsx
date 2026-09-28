import { NavLink, Outlet } from 'react-router-dom'
import { useMe, useSignOut } from '../auth'

const links = [
  { to: '/recipes', label: 'Recipes' },
  { to: '/planner', label: 'Planner' },
  { to: '/grocery', label: 'Grocery' },
]

export default function Layout() {
  const me = useMe().data
  const signOut = useSignOut()
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-20 border-b border-stone-200 bg-cream/90 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center gap-6 px-4 py-3">
          <span className="text-lg font-bold tracking-tight">
            <span className="text-ember">●</span> Cauldron
          </span>
          <nav className="flex gap-1">
            {links.map((l) => (
              <NavLink
                key={l.to}
                to={l.to}
                className={({ isActive }) =>
                  `rounded-full px-3 py-1.5 text-sm font-medium transition ${
                    isActive ? 'bg-ink text-cream' : 'text-stone-600 hover:bg-stone-200'
                  }`
                }
              >
                {l.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-3 text-sm">
            <span className="hidden text-stone-500 sm:inline">{me?.name || me?.email}</span>
            <button onClick={() => signOut.mutate()} className="text-stone-600 hover:text-ember">
              Sign out
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
