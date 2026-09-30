import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, type ReactNode } from 'react'
import { api, type Me } from '../api'
import { loadGoogle, ME, useMe } from '../auth'
import Landing from './Landing'
import { applyTheme, isDark } from '../theme'

export default function AuthGate({ children }: { children: ReactNode }) {
  const me = useMe()
  const theme = me.data?.theme
  useEffect(() => { if (theme) applyTheme(theme) }, [theme])
  const signedIn = me.data === undefined ? undefined : me.data !== null
  useEffect(() => { if (signedIn !== undefined) rememberSignedIn(signedIn) }, [signedIn])
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
    <Landing button={config.data && !clientId
      ? <p className="text-sm text-danger">Google sign-in isn't configured on the server.</p>
      : <div ref={button} />}>
      {signIn.isError && <p className="mt-4 text-sm text-danger">{friendly(signIn.error)}</p>}
    </Landing>
  )
}

/** index.html hides the pre-built sign-in page (dist/prerendered) for people who are signed in,
 *  so they don't glimpse it while the app starts. */
function rememberSignedIn(yes: boolean) {
  try {
    if (yes) localStorage.setItem('signedIn', '1')
    else localStorage.removeItem('signedIn')
  } catch { /* private mode: they may see it for a moment */ }
}

function friendly(e: Error): string {
  return e.message.includes('guest list')
    ? "This Google account isn't on Cauldron's guest list. Ask the owner to add it."
    : "Sign-in failed. Please try again."
}

function Centered({ children }: { children: ReactNode }) {
  return <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-4 text-center">{children}</div>
}
