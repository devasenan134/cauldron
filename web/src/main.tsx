import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Navigate, Route, RouterProvider, createBrowserRouter, createRoutesFromElements } from 'react-router-dom'
import { SignedOut } from './api.ts'
import { ME } from './auth.ts'
import AuthGate from './components/AuthGate.tsx'
import Layout from './components/Layout.tsx'
import './index.css'
import Fridge from './pages/Fridge.tsx'
import Home from './pages/Home.tsx'
import Ingredients, { FoodPage } from './pages/Ingredients.tsx'
import { Privacy, Terms } from './pages/Legal.tsx'
import Profile, { Folder } from './pages/Profile.tsx'
import Settings from './pages/Settings.tsx'
import Grocery from './pages/Grocery.tsx'
import Planner from './pages/Planner.tsx'
import RecipeDetail from './pages/RecipeDetail.tsx'
import RecipeEditor from './pages/RecipeEditor.tsx'
import Recipes from './pages/Recipes.tsx'

// Any request that finds the session gone sends the app back to the sign-in page.
const onError = (e: Error) => {
  if (e instanceof SignedOut) queryClient.setQueryData(ME, null)
}
const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError }),
  mutationCache: new MutationCache({ onError }),
  defaultOptions: {
    queries: { staleTime: 30_000, retry: (n, e) => !(e instanceof SignedOut) && n < 3 },
  },
})

// A data router, so a page can ask before you leave it with unsaved changes (the recipe editor).
// The privacy policy and terms are open to everyone; everything else needs you signed in.
const router = createBrowserRouter(createRoutesFromElements(<>
  <Route path="privacy" element={<Privacy />} />
  <Route path="terms" element={<Terms />} />
  <Route element={<AuthGate><Layout /></AuthGate>}>
    <Route index element={<Home />} />
    <Route path="recipes" element={<Recipes />} />
    <Route path="recipes/new" element={<RecipeEditor />} />
    <Route path="recipes/:id" element={<RecipeDetail />} />
    <Route path="recipes/:id/edit" element={<RecipeEditor />} />
    <Route path="planner" element={<Planner />} />
    <Route path="fridge" element={<Fridge />} />
    <Route path="grocery" element={<Grocery />} />
    <Route path="profile" element={<Profile />} />
    <Route path="folders/:id" element={<Folder />} />
    <Route path="favorites" element={<Folder />} />
    <Route path="settings" element={<Settings />} />
    <Route path="ingredients" element={<Ingredients />} />
    <Route path="ingredients/:id" element={<FoodPage />} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Route>
</>))

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
