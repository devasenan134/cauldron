import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

// The privacy policy and terms of service. Readable without signing in (Google's sign-in
// branding links here), and linked from the sign-in page and Settings on the website and the app.
// They describe the hosted service at cauldron.craftingtable.cc; a self-hosted copy is run by
// whoever hosts it.

const UPDATED = '30 September 2026'
const CONTACT = 'devadas3727@gmail.com'

export function Privacy() {
  return (
    <LegalPage title="Privacy policy">
      <P>
        Cauldron is a cooking app: recipes, meal plans, grocery lists, batch cooking and a meal log. This page
        explains what the hosted service at cauldron.craftingtable.cc keeps about you, why, and how to get it back
        or delete it. It's written to be read, not to hide things.
      </P>

      <H2>Who runs it</H2>
      <P>
        Cauldron is run by one person, Devasenan Murugan, in Canada. He is also the person in charge of protecting
        personal information, as Québec's privacy law (Law 25) requires. Questions, requests and complaints go
        to <Mail />.
      </P>
      <P>If you use a copy of Cauldron that someone else hosts, they run it, and this policy doesn't cover it.</P>

      <H2>What's stored</H2>
      <UL>
        <li><b>Your Google name and email address.</b> You sign in with Google; Cauldron gets your name, your email and
          whether Google has checked it. It never sees your Google password, contacts or anything else.</li>
        <li><b>What you add:</b> your recipes (ingredients, steps, notes and photos), your meal plans and meal log
          (what you ate, when you ate out and the calories you guessed), grocery lists and saved lists, prepped
          ingredients and what's in your fridge, favorites and folders, foods you added or adjusted, and your
          settings (calorie goal, theme, catalog view).</li>
        <li><b>Imports:</b> the links you import from, and a record of each import. Files you upload to import
          (a PDF, a photo, a recipe file) are kept only until the import finishes, then deleted.</li>
        <li><b>Your sign-in session:</b> a random token, stored only as a scrambled hash, with when it expires.</li>
        <li><b>Server logs:</b> the server records requests (IP address, time and address asked for) to help fix
          problems. They aren't analysed or shared, and they're cleared whenever the server software is updated.</li>
      </UL>
      <P>
        Photos you add to your recipes are stored on the server under long, random addresses. They aren't listed
        anywhere, but anyone who has a photo's exact address can open it, so don't upload photos you need to keep
        secret.
      </P>

      <H2>Imports and Google's Gemini</H2>
      <P>
        When you import a recipe, what you import (the link, the web page's text, the PDF, photo or file, or the
        video) is sent to Google's Gemini API, which reads it and writes the recipe. For web pages and videos the
        Cauldron server fetches the page first. Google handles what it receives under
        its <A href="https://ai.google.dev/gemini-api/terms">Gemini API terms</A>, which allow it to keep the data
        for a limited time and, depending on the plan, to use it to improve its services. Don't import anything
        private. Nothing is sent to Gemini unless you start an import.
      </P>

      <H2>Cookies and storage</H2>
      <UL>
        <li>The website sets one cookie, <code>cauldron_session</code>, to keep you signed in. It lasts 30 days, can't be
          read by scripts on the page (HttpOnly), and is only sent over HTTPS.</li>
        <li>The Android app doesn't use a cookie: it keeps a sign-in token in the app's private storage and sends it
          with each request.</li>
        <li>The website remembers your theme (light or dark) in your browser's local storage, so the page doesn't
          flash the wrong colours while it loads.</li>
        <li>The website loads its fonts from Google Fonts and the sign-in button from Google, so your browser
          contacts Google when you open it. Google's <A href="https://policies.google.com/privacy">privacy policy</A> covers that.</li>
        <li>The starter recipes' photos come from Wikimedia Commons. The server keeps its own copy, but until it has
          one, your browser loads the photo from Wikimedia.</li>
      </UL>

      <H2>What Cauldron doesn't do</H2>
      <UL>
        <li>No ads.</li>
        <li>No tracking or analytics: no trackers, no profiles of what you do.</li>
        <li>Your data is never sold, rented or shared for marketing.</li>
      </UL>
      <P>
        Your recipes, plans and lists are private to your account. The recipe library everyone sees is written
        by Cauldron, not by other users. As the administrator, Devasenan can technically reach the database, and
        only looks at it to keep the service running or when you ask for help.
      </P>

      <H2>Where it's hosted</H2>
      <P>
        Cauldron runs on its owner's own server, at home in Canada, and your data is stored there. Traffic
        reaches it through <A href="https://www.cloudflare.com/privacypolicy/">Cloudflare</A>, which protects the connection and
        may pass it through servers in other countries on the way. Cloudflare sees your IP address, like any
        network provider, but not what's stored. Google provides sign-in and imports, as described above.
      </P>

      <H2>How long it's kept</H2>
      <P>
        Everything is kept for as long as you have an account. When you delete your account, it's removed from
        the live database straight away, along with your photos. Backups of the database may still hold a copy
        for up to 30 days, until they're replaced.
      </P>

      <H2>Get your data, or delete it</H2>
      <UL>
        <li><b>Download it:</b> Settings → Account → <i>Download my data</i> gives you a file (JSON) with all of it, on the
          website and in the app.</li>
        <li><b>Change it:</b> edit or delete anything in the app at any time.</li>
        <li><b>Delete your account:</b> Settings → Account → <i>Delete my account</i>. It removes your account and everything
          in it, and can't be undone.</li>
      </UL>
      <P>
        You can also ask for any of this by email, or ask what's held about you, at <Mail />. If you're not happy
        with the answer, you can complain to the <A href="https://www.cai.gouv.qc.ca/">Commission d'accès à l'information du
        Québec</A> or the <A href="https://www.priv.gc.ca/">Office of the Privacy Commissioner of Canada</A>.
      </P>

      <H2>Children</H2>
      <P>Cauldron isn't meant for children under 14. Don't sign up if you're younger than that.</P>

      <H2>Changes</H2>
      <P>
        If this policy changes, the new version goes here with a new date. Big changes will also be announced in
        the app before they apply.
      </P>
    </LegalPage>
  )
}

export function Terms() {
  return (
    <LegalPage title="Terms of service">
      <P>
        These terms are the deal for using the hosted Cauldron at cauldron.craftingtable.cc. By using it, you
        agree to them. They're short on purpose.
      </P>

      <H2>The service</H2>
      <UL>
        <li>Cauldron is free. It's run by one person, Devasenan Murugan, on his own hardware.</li>
        <li>It's provided as it is, with no warranty. It may have bugs, be down now and then, change, or one day
          close. If it's ever going to close, you'll be told ahead of time, so you can download your data.</li>
        <li>Calories and macros are estimates from USDA data and your recipes. They're a guide, not medical or
          dietary advice.</li>
      </UL>

      <H2>Your account</H2>
      <UL>
        <li>You sign in with a Google account, and you need to be at least 14.</li>
        <li>You're responsible for what happens under your account; keep your Google account secure.</li>
        <li>You can delete your account whenever you like (Settings → Account).</li>
      </UL>

      <H2>Use it fairly</H2>
      <P>Please don't:</P>
      <UL>
        <li>break the law with it, or store anything illegal, hateful or sexually explicit;</li>
        <li>upload other people's private information, or photos of people who haven't agreed;</li>
        <li>try to break into it, get around its limits (like the daily import and photo limits), overload it, or
          scrape it with automated tools;</li>
        <li>use imports to copy other people's recipe collections in bulk.</li>
      </UL>
      <P>
        The owner may suspend or delete accounts that break these rules or put the service or other people at
        risk. Where it's reasonable, you'll get a warning first and a chance to download your data.
      </P>

      <H2>What you add is yours</H2>
      <P>
        You own your recipes, photos, plans and everything else you put in. You let Cauldron store, process and
        show them to you, and send them to Google's Gemini when you import something, only so the service can
        work. Cauldron doesn't claim anything else.
      </P>

      <H2>Imports and other people's recipes</H2>
      <P>
        When you import a recipe from a video, a web page, a PDF or a photo, you're responsible for having the
        right to. Recipes written by someone else still belong to them: keep imports for your own cooking, and
        don't republish them. If you think something in Cauldron infringes your rights, write to <Mail />.
      </P>

      <H2>The starter recipes</H2>
      <P>
        The recipe library everyone sees was written for Cauldron. Its text is licensed under
        the <A href="https://www.apache.org/licenses/LICENSE-2.0">Apache License 2.0</A>, like Cauldron's code, so you can reuse
        it. Its photos belong to their photographers and are used under the free licence shown under each photo
        (CC0, public domain, CC BY or CC BY-SA); reusing a photo means following that licence.
      </P>

      <H2>Liability</H2>
      <P>
        As far as the law allows, the owner isn't liable for any loss that comes from using Cauldron, including
        lost data: keep your own copy of anything important (Settings → Account → Download my data). Nothing
        here takes away rights you have by law, including as a consumer in Québec.
      </P>

      <H2>The rest</H2>
      <UL>
        <li>These terms are governed by the laws of Québec and of Canada.</li>
        <li>If the terms change, the new version goes here with a new date, and big changes are announced in the
          app first.</li>
        <li>Questions: <Mail />.</li>
      </UL>
    </LegalPage>
  )
}

function LegalPage({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="min-h-screen bg-cream">
      <header className="border-b border-stone-200 bg-paper">
        <div className="mx-auto flex max-w-2xl items-center gap-2 px-5 py-3">
          <Link to="/" className="flex items-center gap-2">
            <img src="/favicon.svg" alt="" className="h-9 w-9" />
            <span className="font-display text-2xl font-extrabold uppercase tracking-tight">Cauldron</span>
          </Link>
        </div>
      </header>
      <main className="rise mx-auto max-w-2xl px-5 pb-16 pt-8 leading-relaxed">
        <h1 className="font-display text-4xl font-extrabold sm:text-5xl">{title}</h1>
        <p className="mt-2 text-sm text-stone-500">Last updated {UPDATED}</p>
        {children}
        <LegalLinks className="mt-12 border-t border-stone-200 pt-6" />
      </main>
    </div>
  )
}

/** "Privacy · Terms", for the sign-in page, Settings and the bottom of these pages. */
export function LegalLinks({ className = '' }: { className?: string }) {
  return (
    <p className={`text-sm text-stone-500 ${className}`}>
      <Link to="/privacy" className="hover:underline">Privacy policy</Link>
      <span className="mx-2">·</span>
      <Link to="/terms" className="hover:underline">Terms of service</Link>
    </p>
  )
}

const H2 = ({ children }: { children: ReactNode }) => <h2 className="mt-9 font-display text-2xl font-bold">{children}</h2>
const P = ({ children }: { children: ReactNode }) => <p className="mt-3 text-stone-700">{children}</p>
const UL = ({ children }: { children: ReactNode }) => <ul className="mt-3 list-disc space-y-2 pl-5 text-stone-700">{children}</ul>
const A = ({ href, children }: { href: string; children: ReactNode }) =>
  <a href={href} target="_blank" rel="noreferrer" className="font-semibold text-ember hover:underline">{children}</a>
const Mail = () => <a href={`mailto:${CONTACT}`} className="font-semibold text-ember hover:underline">{CONTACT}</a>
