import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, type ReactNode } from 'react'
import { api, type Me } from '../api'
import { loadGoogle, ME, useMe } from '../auth'
import { applyTheme, isDark } from '../theme'

export default function AuthGate({ children }: { children: ReactNode }) {
  const me = useMe()
  const theme = me.data?.theme
  useEffect(() => { if (theme) applyTheme(theme) }, [theme])
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
      gid.renderButton(button.current, { theme: isDark() ? 'filled_black' : 'outline', size: 'large', shape: 'pill', text: 'continue_with', width: 320 })
    })
    return () => {
      cancelled = true
    }
  }, [clientId, mutate])

  return (
    <div className="flex min-h-screen flex-col bg-cream px-7 py-10 sm:items-center sm:justify-center">
      <div className="flex w-full max-w-md flex-1 flex-col sm:flex-none">
        <div className="flex-[0.6] sm:hidden" />
        <img src="/favicon.svg" alt="" className="h-24 w-24 rounded-[28px] bg-paper shadow-[0_8px_24px_rgba(0,0,0,0.08)]" />
        <h1 className="mt-7 font-display text-5xl font-extrabold leading-[1.05]">Cook once,<br />eat all week.</h1>
        <p className="mt-3 text-lg text-stone-500">Recipes, meal plans, batch cooking and groceries: Cauldron.</p>
        <div className="flex-1 sm:hidden" />
        <div className="mt-10 min-h-11">
          {config.data && !clientId ? (
            <p className="text-sm text-danger">Google sign-in isn't configured on the server.</p>
          ) : (
            <div ref={button} />
          )}
        </div>
        {signIn.isError && <p className="mt-4 text-sm text-danger">{friendly(signIn.error)}</p>}
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
