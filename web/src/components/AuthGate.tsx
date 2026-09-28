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
      gid.renderButton(button.current, { theme: 'outline', size: 'large', shape: 'pill', text: 'signin_with' })
    })
    return () => {
      cancelled = true
    }
  }, [clientId, mutate])

  return (
    <Centered>
      <div className="text-3xl font-bold tracking-tight">
        <span className="text-ember">●</span> Cauldron
      </div>
      <p className="text-sm text-stone-600">Recipes, meal plans and groceries. Sign in to continue.</p>
      {config.data && !clientId ? (
        <p className="text-sm text-red-700">Google sign-in isn't configured on the server.</p>
      ) : (
        <div ref={button} className="min-h-11" />
      )}
      {signIn.isError && <p className="max-w-xs text-sm text-red-700">{friendly(signIn.error)}</p>}
    </Centered>
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
