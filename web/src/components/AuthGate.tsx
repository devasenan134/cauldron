import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, type ReactNode } from 'react'
import { api, type Me } from '../api'
import { loadGoogle, ME, useMe } from '../auth'

export default function AuthGate({ children }: { children: ReactNode }) {
  const me = useMe()
  if (me.isPending) return null
  if (me.isError) return <Centered><p className="text-sm text-red-700">{String(me.error)}</p></Centered>
  return me.data ? children : <SignIn />
}

function SignIn() {
  const qc = useQueryClient()
  const button = useRef<HTMLDivElement>(null)
  const config = useQuery({ queryKey: ['auth-config'], queryFn: api.authConfig, staleTime: Infinity })
  const signIn = useMutation({
    mutationFn: api.signIn,
    onSuccess: (me: Me) => qc.setQueryData(ME, me),
  })

  const clientId = config.data?.google_client_id
  const { mutate } = signIn
  useEffect(() => {
    if (!clientId) return
    let cancelled = false
    loadGoogle().then((gid) => {
      if (cancelled || !button.current) return
      gid.initialize({ client_id: clientId, callback: (r) => mutate(r.credential) })
      gid.renderButton(button.current, { theme: 'filled_black', size: 'large', shape: 'pill', text: 'continue_with', width: 280 })
    })
    return () => {
      cancelled = true
    }
  }, [clientId, mutate])

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-gradient-to-b from-[#2a211a] to-ink px-6 text-center">
      {/* A warm glow, like embers under the pot. */}
      <div className="pointer-events-none absolute left-1/2 top-1/2 h-[520px] w-[520px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[radial-gradient(circle,rgba(234,88,12,0.35),transparent_65%)]" />
      <div className="relative flex flex-col items-center">
        <img src="/favicon.svg" alt="" className="h-36 w-36 rounded-[40px] shadow-2xl" />
        <h1 className="mt-6 font-display text-6xl font-extrabold tracking-tight text-cream">Cauldron</h1>
        <p className="mt-1 text-lg text-cream/70">Cook once, eat all week.</p>
        <div className="mt-10 min-h-11">
          {config.data && !clientId ? (
            <p className="text-sm text-red-300">Google sign-in isn't configured on the server.</p>
          ) : (
            <div ref={button} />
          )}
        </div>
        {signIn.isError && <p className="mt-4 max-w-xs text-sm text-red-300">{friendly(signIn.error)}</p>}
      </div>
    </div>
  )
}

function friendly(e: Error): string {
  return e.message.includes('guest list')
    ? "This Google account isn't on Cauldron's guest list. Ask the owner to add it."
    : "Sign-in failed. Please try again."
}

function Centered({ children }: { children: ReactNode }) {
  return <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-4 text-center">{children}</div>
}
