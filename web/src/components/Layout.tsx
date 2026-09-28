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
  person: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM7.07 18.28c.43-.9 3.05-1.78 4.93-1.78s4.51.88 4.93 1.78A7.9 7.9 0 0 1 12 20c-1.86 0-3.57-.64-4.93-1.72zm11.29-1.45c-1.43-1.74-4.9-2.33-6.36-2.33s-4.93.59-6.36 2.33A7.95 7.95 0 0 1 4 12c0-4.41 3.59-8 8-8s8 3.59 8 8c0 1.82-.62 3.49-1.64 4.83zM12 6c-1.94 0-3.5 1.56-3.5 3.5S10.06 13 12 13s3.5-1.56 3.5-3.5S13.94 6 12 6zm0 5c-.83 0-1.5-.67-1.5-1.5S11.17 8 12 8s1.5.67 1.5 1.5S12.83 11 12 11z',
  cart: 'M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zM1 2v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49A1.003 1.003 0 0 0 20 4H5.21l-.94-2H1zm16 16c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2z',
}
const Icon = ({ d }: { d: string }) => <svg viewBox="0 0 24 24" className="h-[22px] w-[22px] fill-current" aria-hidden><path d={d} /></svg>

const links = [
  { to: '/', label: 'Home', icon: ICON.home },
  { to: '/recipes', label: 'Recipes', icon: ICON.book },
  { to: '/planner', label: 'Plan', icon: ICON.calendar },
  { to: '/fridge', label: 'Fridge', icon: ICON.fridge },
  { to: '/profile', label: 'Profile', icon: ICON.person },
]

export default function Layout() {
  const grocery = useQuery({ queryKey: ['grocery'], queryFn: api.grocery })
  const toBuy = grocery.data?.filter((i) => !i.checked).length ?? 0
  const badge = (to: string) => (to === '/grocery' && toBuy > 0 ? toBuy : 0) // (grocery is on Home now)

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-30 border-b border-stone-200 bg-paper/90 backdrop-blur-md">
        <div className="mx-auto flex max-w-7xl items-center gap-4 px-5 py-3">
          <NavLink to="/" className="flex items-center gap-2">
            <img src="/favicon.svg" alt="" className="h-9 w-9" />
            <span className="font-display text-2xl font-extrabold uppercase tracking-tight">Cauldron</span>
          </NavLink>
          {/* Desktop: pill nav in the header */}
          <nav className="mx-auto hidden items-center gap-1 md:flex">
            {links.map((l) => (
              <NavLink key={l.to} to={l.to} end={l.to === '/'}
                className={({ isActive }) =>
                  `press relative rounded-full px-4 py-2 text-sm font-semibold uppercase tracking-wide transition-colors ${isActive ? 'bg-ink text-cream' : 'text-stone-500 hover:text-ink'}`}>
                {l.label}
                {badge(l.to) > 0 && (
                  <span className="absolute -right-1 -top-1 grid h-5 min-w-5 place-items-center rounded-full bg-ink px-1 text-[10px] font-bold text-cream ring-2 ring-paper">{badge(l.to)}</span>
                )}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto md:ml-0"><Avatar /></div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-5 pb-28 pt-6 md:pb-16">
        <Outlet />
      </main>

      {/* Phones: the bottom bar, like the app's */}
      <nav className="fixed inset-x-0 bottom-0 z-30 border-t border-stone-200 bg-paper md:hidden" style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}>
        <div className="flex h-16">
          {links.map((l) => (
            <NavLink key={l.to} to={l.to} end={l.to === '/'}
              className={({ isActive }) => `press relative flex flex-1 flex-col items-center justify-center gap-0.5 text-[11px] ${isActive ? 'font-semibold text-ink' : 'font-medium text-stone-400'}`}>
              <Icon d={l.icon} />
              {l.label}
              {badge(l.to) > 0 && (
                <span className="absolute left-1/2 top-1.5 ml-2 grid h-5 min-w-5 place-items-center rounded-full bg-ink px-1 text-[10px] font-bold text-cream">{badge(l.to)}</span>
              )}
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  )
}
