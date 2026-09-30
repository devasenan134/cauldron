import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, SignedOut } from './api'

// Minimal types for Google Identity Services (https://accounts.google.com/gsi/client).
type GoogleId = {
  initialize: (o: { client_id: string; callback: (r: { credential: string }) => void }) => void
  renderButton: (el: HTMLElement, o: Record<string, string | number>) => void
  disableAutoSelect: () => void
}
declare global {
  interface Window {
    google?: { accounts: { id: GoogleId } }
  }
}

let gisScript: Promise<GoogleId> | undefined
export function loadGoogle(): Promise<GoogleId> {
  gisScript ??= new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = 'https://accounts.google.com/gsi/client'
    s.async = true
    s.onload = () => resolve(window.google!.accounts.id)
    s.onerror = () => reject(new Error('could not load Google sign-in'))
    document.head.append(s)
  })
  return gisScript
}

export const ME = ['me']

/** The signed-in user; null when signed out. */
export function useMe() {
  return useQuery({
    queryKey: ME,
    queryFn: () => api.me().catch((e) => (e instanceof SignedOut ? null : Promise.reject(e))),
    staleTime: Infinity,
  })
}

export function useSignOut() {
  return useSignedOutAfter(api.signOut)
}

/** Delete your account and everything in it, then sign out. */
export function useDeleteAccount() {
  return useSignedOutAfter(api.deleteAccount)
}

function useSignedOutAfter(mutationFn: () => Promise<unknown>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      window.google?.accounts.id.disableAutoSelect()
      qc.setQueryData(ME, null)
      // Drop the previous user's data (but keep the query the gate is watching).
      qc.removeQueries({ predicate: (q) => q.queryKey[0] !== ME[0] })
    },
  })
}
