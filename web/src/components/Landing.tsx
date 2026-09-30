import type { ReactNode } from 'react'
import { LegalLinks } from '../pages/Legal'

// The page people see before they sign in: what Cauldron is, the sign-in button and the legal
// links. No data or browser-only code here: it's also built into plain HTML (src/prerender.tsx),
// because Google's review of the sign-in branding reads the page without running scripts.

const FEATURES: [string, string, string][] = [
  ['📖', 'Your recipe book', 'Write your recipes, start from 135 home recipes, or import one from a YouTube video, an Instagram Reel, a web page, a PDF or a photo.'],
  ['🔥', 'Calories and macros', 'Every ingredient, dish and portion, from USDA nutrition data. Log what you eat against a daily goal.'],
  ['🗓️', 'Meal planner', 'Drag recipes onto the week. The grocery list writes itself, sorted by aisle, and works offline on your phone.'],
  ['🍱', 'Batch cooking', 'Cook once, eat for days: Cauldron tracks what is in the fridge and how many portions are left.'],
]

/** button: the Google sign-in button (an empty box of the same size when pre-built). */
export default function Landing({ button, children }: { button: ReactNode; children?: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-cream px-7 py-10 sm:items-center sm:justify-center">
      <div className="flex w-full max-w-md flex-1 flex-col sm:flex-none">
        <div className="flex-[0.6] sm:hidden" />
        <img src="/favicon.svg" alt="" className="h-24 w-24 rounded-[28px] bg-paper shadow-[0_8px_24px_rgba(0,0,0,0.08)]" />
        <h1 className="mt-7 font-display text-5xl font-extrabold leading-[1.05]">Cook once,<br />eat all week.</h1>
        <p className="mt-3 text-lg text-stone-500">Recipes, meal plans, batch cooking and groceries: Cauldron.</p>
        <div className="flex-1 sm:hidden" />
        <div className="mt-10 min-h-11">{button}</div>
        {children}
        <LegalLinks className="mt-8" />
      </div>
      <section className="mx-auto w-full max-w-md pb-4 pt-14">
        <h2 className="font-display text-2xl font-bold">What Cauldron does</h2>
        <div className="mt-4 grid gap-3">
          {FEATURES.map(([emoji, title, body]) => (
            <div key={title} className="rounded-3xl bg-paper p-5 ring-1 ring-stone-200">
              <p className="text-2xl">{emoji}</p>
              <p className="mt-2 font-semibold">{title}</p>
              <p className="mt-1 text-sm text-stone-500">{body}</p>
            </div>
          ))}
        </div>
        <p className="mt-5 text-sm text-stone-500">
          Free, with a website and an Android app. Sign in with Google: Cauldron only gets your name and email address,
          to keep your recipes and plans in your account (see the <a href="/privacy" className="font-semibold text-ink underline">privacy policy</a>).
          Cauldron is open source, and you can host your own copy: <a href="https://github.com/devasenan134/cauldron" className="font-semibold text-ink underline">source code on GitHub</a>.
        </p>
      </section>
    </div>
  )
}
