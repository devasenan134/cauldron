import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { SignedOut } from './api.ts'
import { ME } from './auth.ts'
import AuthGate from './components/AuthGate.tsx'
import Layout from './components/Layout.tsx'
import './index.css'
import Fridge from './pages/Fridge.tsx'
import Grocery from './pages/Grocery.tsx'
import Planner from './pages/Planner.tsx'
import RecipeDetail from './pages/RecipeDetail.tsx'
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

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthGate>
        <BrowserRouter>
          <Routes>
            <Route element={<Layout />}>
              <Route index element={<Navigate to="/recipes" replace />} />
              <Route path="recipes" element={<Recipes />} />
              <Route path="recipes/:id" element={<RecipeDetail />} />
              <Route path="planner" element={<Planner />} />
              <Route path="fridge" element={<Fridge />} />
              <Route path="grocery" element={<Grocery />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthGate>
    </QueryClientProvider>
  </StrictMode>,
)
