import { NavLink, Outlet } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api'
import { Avatar } from './ui'

// Material icons (Apache 2.0), the same ones the app's tab bar uses.
const ICON = {
  home: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z',
  book: 'M21 5c-1.11-.35-2.33-.5-3.5-.5-1.95 0-4.05.4-5.5 1.5-1.45-1.1-3.55-1.5-5.5-1.5S2.45 4.9 1 6v14.65c0 .25.25.5.5.5.1 0 .15-.05.25-.05C3.1 20.45 5.05 20 6.5 20c1.95 0 4.05.4 5.5 1.5 1.35-.85 3.8-1.5 5.5-1.5 1.65 0 3.35.3 4.75 1.05.1.05.15.05.25.05.25 0 .5-.25.5-.5V6c-.6-.45-1.25-.75-2-1zm0 13.5c-1.1-.35-2.3-.5-3.5-.5-1.7 0-4.15.65-5.5 1.5V8c1.35-.85 3.8-1.5 5.5-1.5 1.2 0 2.4.15 3.5.5v11.5z',
  calendar: 'M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zM9 14H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2zm-8 4H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2z',
  fridge: 'M18 2.01 6 2c-1.1 0-2 .89-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.11-.9-1.99-2-1.99zM18 20H6v-9.02h12V20zm0-11H6V4h12v5zM8 5h2v3H8zm0 7h2v5H8z',
  cart: 'M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zM1 2v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49A1.003 1.003 0 0 0 20 4H5.21l-.94-2H1zm16 16c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2z',
}
const Icon = ({ d }: { d: string }) => <svg viewBox="0 0 24 24" className="h-[22px] w-[22px] fill-current" aria-hidden><path d={d} /></svg>

const links = [
  { to: '/', label: 'Home', icon: ICON.home },
  { to: '/recipes', label: 'Recipes', icon: ICON.book },
  { to: '/planner', label: 'Plan', icon: ICON.calendar },
  { to: '/fridge', label: 'Fridge', icon: ICON.fridge },
  { to: '/grocery', label: 'Grocery', icon: ICON.cart },
]

export default function Layout() {
  const grocery = useQuery({ queryKey: ['grocery'], queryFn: api.grocery })
  const toBuy = grocery.data?.filter((i) => !i.checked).length ?? 0
  const badge = (to: string) => (to === '/grocery' && toBuy > 0 ? toBuy : 0)

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-30 bg-cream/85 backdrop-blur-md">
        <div className="mx-auto flex max-w-7xl items-center gap-4 px-5 py-3">
          <NavLink to="/" className="flex items-center gap-2">
            <img src="/favicon.svg" alt="" className="h-9 w-9" />
            <span className="font-display text-2xl font-extrabold tracking-tight">Cauldron</span>
          </NavLink>
          {/* Desktop: pill nav in the header */}
          <nav className="mx-auto hidden items-center gap-1 rounded-full bg-ink p-1.5 shadow-lg md:flex">
            {links.map((l) => (
              <NavLink key={l.to} to={l.to} end={l.to === '/'}
                className={({ isActive }) =>
                  `press relative rounded-full px-4 py-2 text-sm font-semibold transition-colors ${isActive ? 'bg-ember-bright text-white' : 'text-cream/75 hover:text-cream'}`}>
                {l.label}
                {badge(l.to) > 0 && (
                  <span className="absolute -right-1 -top-1 grid h-5 min-w-5 place-items-center rounded-full bg-ember-bright px-1 text-[10px] font-bold text-white ring-2 ring-ink">{badge(l.to)}</span>
                )}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto md:ml-0"><Avatar /></div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-5 pb-32 pt-4 md:pb-16">
        <Outlet />
      </main>

      {/* Phones: the floating pill at the bottom, like the app */}
      <nav className="fixed inset-x-0 bottom-4 z-30 flex justify-center px-4 md:hidden" style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}>
        <div className="flex items-center gap-1 rounded-full bg-ink p-1.5 shadow-2xl">
          {links.map((l) => (
            <NavLink key={l.to} to={l.to} end={l.to === '/'} aria-label={l.label}
              className={({ isActive }) =>
                `press relative flex h-12 items-center gap-2 rounded-full px-3.5 text-sm font-semibold transition-all ${isActive ? 'bg-ember-bright text-white' : 'text-cream/75'}`}>
              {({ isActive }) => (
                <>
                  <Icon d={l.icon} />
                  {isActive && <span className="rise">{l.label}</span>}
                  {badge(l.to) > 0 && !isActive && (
                    <span className="absolute -right-0.5 -top-0.5 grid h-5 min-w-5 place-items-center rounded-full bg-ember-bright px-1 text-[10px] font-bold text-white ring-2 ring-ink">{badge(l.to)}</span>
                  )}
                </>
              )}
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  )
}
