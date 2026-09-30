// Builds the public pages into plain HTML (npm run build → dist/prerendered/), so they read
// without scripts: Google's review of the sign-in branding checks the home page and the privacy
// policy that way. The server sends these for /, /privacy and /terms; the app then starts on top.
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import Landing from './components/Landing'
import { Privacy, Terms } from './pages/Legal'

export const PAGES = {
  index: {
    path: '/',
    title: 'Cauldron: free meal planner, recipes and calorie tracker',
    description: 'Cauldron is a free cooking app: your recipe book, calories and macros per portion, a meal planner that writes the grocery list, and batch cooking. Website and Android app.',
    element: <Landing button={<div className="h-11" />} />,
  },
  privacy: {
    path: '/privacy',
    title: 'Privacy policy · Cauldron',
    description: 'What Cauldron keeps about you, including the Google account data it uses to sign you in, why, and how to download or delete it.',
    element: <Privacy />,
  },
  terms: {
    path: '/terms',
    title: 'Terms of service · Cauldron',
    description: 'The terms for using the hosted Cauldron cooking app.',
    element: <Terms />,
  },
}

export function render(name: keyof typeof PAGES): string {
  const page = PAGES[name]
  return renderToString(<MemoryRouter initialEntries={[page.path]}>{page.element}</MemoryRouter>)
}
