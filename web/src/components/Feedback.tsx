import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, type Feedback, type FeedbackType } from '../api'
import { Button, inputCls } from './ui'

const ISSUES_URL = 'https://github.com/devasenan134/cauldron/issues/new/choose'
const FEEDBACK = ['feedback']

/** What's attached to a report, so it says where it came from. */
function meta(): Record<string, string> {
  return { platform: 'web', version: `web, built ${__BUILD_DATE__}`, browser: navigator.userAgent }
}

/** Settings → Feedback: report a bug or ask for a feature, and see what you've sent. */
export function FeedbackForm({ owner }: { owner: boolean }) {
  const qc = useQueryClient()
  const [type, setType] = useState<FeedbackType>('bug')
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [sent, setSent] = useState(false)
  const send = useMutation({
    mutationFn: () => api.sendFeedback({ type, title: title.trim(), body: body.trim(), meta: meta() }),
    onSuccess: () => { setTitle(''); setBody(''); setSent(true); qc.invalidateQueries({ queryKey: FEEDBACK }) },
  })
  const mine = useQuery({ queryKey: FEEDBACK, queryFn: api.feedback, enabled: !owner })

  return (
    <div>
      <div className="flex rounded-full bg-sand p-1">
        {([['bug', 'Report a bug'], ['feature', 'Request a feature']] as const).map(([t, label]) => (
          <button key={t} onClick={() => { setType(t); setSent(false) }}
            className={`press flex-1 rounded-full py-2.5 text-sm transition-colors ${type === t ? 'bg-paper font-bold shadow-sm' : 'font-medium text-stone-500'}`}>
            {label}
          </button>
        ))}
      </div>
      <input value={title} maxLength={200} onChange={(e) => { setTitle(e.target.value); setSent(false) }}
        placeholder={type === 'bug' ? 'What went wrong, in a few words' : 'What would you like Cauldron to do?'}
        className={`${inputCls} mt-3 w-full`} />
      <textarea value={body} maxLength={5000} rows={4} onChange={(e) => { setBody(e.target.value); setSent(false) }}
        placeholder={type === 'bug' ? 'What did you do, what happened, and what did you expect?' : 'Tell me more: how would you use it?'}
        className="mt-2 w-full rounded-3xl border-0 bg-paper px-5 py-3 text-base shadow-sm ring-1 ring-stone-200 outline-none placeholder:text-stone-400 focus:ring-2 focus:ring-ember-bright/50" />
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <Button variant="accent" disabled={!title.trim() || send.isPending} onClick={() => send.mutate()}>{send.isPending ? 'Sending…' : 'Send'}</Button>
        <a href={ISSUES_URL} target="_blank" rel="noreferrer" className="text-sm font-semibold text-ember hover:underline">Or open an issue on GitHub</a>
      </div>
      {sent && <p className="mt-3 text-sm font-semibold text-ember">Thanks! It's been sent.</p>}
      {send.isError && <p className="mt-3 text-sm text-danger">{reason(send.error)}</p>}
      <p className="mt-2 text-xs text-stone-500">The website's version and your browser are attached, to help find the problem.</p>
      {!owner && !!mine.data?.length && (
        <div className="mt-4 border-t border-stone-200 pt-3">
          <p className="text-sm font-semibold">You've sent</p>
          <ul className="mt-1 space-y-1">
            {mine.data.slice(0, 5).map((f) => (
              <li key={f.id} className="flex items-center gap-2 text-sm">
                <span className="flex-1 truncate">{f.type === 'bug' ? '🐞' : '💡'} {f.title}</span>
                <span className={`text-xs font-semibold ${f.status === 'done' ? 'text-ember' : 'text-stone-500'}`}>{f.status === 'done' ? 'Done' : 'Open'}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

/** The owner's view: everyone's reports, open ones first, to mark done. */
export function FeedbackInbox() {
  const qc = useQueryClient()
  const all = useQuery({ queryKey: FEEDBACK, queryFn: api.feedback })
  const mark = useMutation({
    mutationFn: ({ id, status }: { id: number; status: Feedback['status'] }) => api.setFeedbackStatus(id, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: FEEDBACK }),
  })
  if (all.isPending) return <p className="text-stone-500">Loading…</p>
  if (!all.data?.length) return <p className="text-stone-500">Nothing yet.</p>
  const open = all.data.filter((f) => f.status === 'open').length
  return (
    <div>
      <p className="text-sm text-stone-500">{open} open · {all.data.length} in all</p>
      <ul className="mt-2 divide-y divide-stone-200">
        {all.data.map((f) => (
          <li key={f.id} className={`py-3 ${f.status === 'done' ? 'opacity-60' : ''}`}>
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1">
                <p className="font-semibold">{f.type === 'bug' ? '🐞' : '💡'} {f.title}</p>
                {f.body && <p className="mt-1 whitespace-pre-line text-sm text-stone-700">{f.body}</p>}
                <p className="mt-1 text-xs text-stone-500">
                  {f.user_name || f.user_email} · {f.user_email} · {new Date(f.created_at + (f.created_at.endsWith('Z') || f.created_at.includes('+') ? '' : 'Z')).toLocaleDateString()}
                  {f.meta.version && ` · ${f.meta.version}`}{f.meta.device && ` · ${f.meta.device}`}{f.meta.android && ` · Android ${f.meta.android}`}
                </p>
              </div>
              <Button variant={f.status === 'done' ? 'ghost' : 'soft'} disabled={mark.isPending}
                onClick={() => mark.mutate({ id: f.id, status: f.status === 'done' ? 'open' : 'done' })}>
                {f.status === 'done' ? 'Reopen' : 'Mark done'}
              </Button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

function reason(e: Error): string {
  return e.message.match(/"detail":\s*"([^"]*)"/)?.[1] ?? "Couldn't send it. Please try again."
}
