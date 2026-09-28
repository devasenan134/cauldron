export type Macros = { kcal: number; protein: number; fat: number; carbs: number }

export type RecipeSummary = {
  id: number
  title: string
  image_url: string | null
  source: string
  cuisine: string | null
  category: string | null
  total_minutes: number | null
  servings: number | null
  tags: string[]
  kcal_per_serving: number | null
}

export type Ingredient = {
  id: number
  position: number
  group: string | null
  name: string
  note: string
  label: string
  grams: number | null
  grams_source: 'given' | 'parts' | 'portion' | 'estimate' | 'manual' | null
  aisle: string | null
  food_id: number | null
  food_name: string | null
  nutrition: Macros | null
}

export type Step = { id: number; position: number; title: string; text: string }

export type RecipeDetail = Omit<RecipeSummary, 'kcal_per_serving'> & {
  can_edit: boolean
  slug: string
  source_url: string | null
  video_url: string | null
  author: string | null
  description: string
  yield_text: string | null
  notes: string
  source_nutrition: Partial<Record<'calories' | 'protein' | 'fat' | 'carbohydrates', number>> | null
  ingredients: Ingredient[]
  steps: Step[]
  nutrition: {
    total: Macros
    per_serving: Macros | null
    left_out: string[]
    estimated: string[]
  }
}

export type Food = { id: number; name: string; source: string; kcal: number; protein: number; fat: number; carbs: number }

export type PlanEntry = {
  id: number
  day: string | null
  position: number
  recipe_id: number | null
  title: string
  image_url: string | null
  servings: number
  kcal_per_serving: number | null
  kcal: number | null
}

export type Plan = { days: Record<string, PlanEntry[]>; queue: PlanEntry[] }

export type GroceryItem = {
  id: number
  name: string
  amount: string
  aisle: string
  checked: boolean
  manual: boolean
  sources: string[]
}

export type Me = { email: string; name: string; is_owner: boolean }

/** The session is missing or expired; the app shows the sign-in page. */
export class SignedOut extends Error {}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(`/api${path}`, {
    method,
    headers: body === undefined ? undefined : { 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (res.status === 401) throw new SignedOut()
  if (!res.ok) throw new Error(`${method} ${path}: ${res.status} ${await res.text()}`)
  return res.json() as Promise<T>
}

const qs = (params: Record<string, string | number | boolean | undefined>) =>
  new URLSearchParams(
    Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== '')
      .map(([k, v]) => [k, String(v)]),
  ).toString()

export const api = {
  authConfig: () => request<{ google_client_id: string }>('GET', '/auth/config'),
  me: () => request<Me>('GET', '/auth/me'),
  signIn: (credential: string) => request<Me>('POST', '/auth/google', { credential }),
  signOut: () => request<{ ok: boolean }>('POST', '/auth/logout'),

  recipes: (p: { q?: string; cuisine?: string; category?: string } = {}) =>
    request<RecipeSummary[]>('GET', `/recipes?${qs(p)}`),
  facets: () => request<{ cuisines: string[]; categories: string[] }>('GET', '/recipes/facets'),
  recipe: (id: number) => request<RecipeDetail>('GET', `/recipes/${id}`),
  patchIngredient: (id: number, patch: { grams?: number | null; food_id?: number | null }) =>
    request<RecipeDetail>('PATCH', `/ingredients/${id}`, patch),
  foods: (q: string) => request<Food[]>('GET', `/foods?${qs({ q })}`),

  plan: (start: string, days = 7) => request<Plan>('GET', `/plan?${qs({ start, days })}`),
  addEntry: (e: { day: string | null; recipe_id?: number; title?: string; servings?: number; position?: number }) =>
    request<PlanEntry>('POST', '/plan', e),
  updateEntry: (id: number, patch: { day?: string | null; position?: number; servings?: number; title?: string }) =>
    request<PlanEntry>('PATCH', `/plan/${id}`, patch),
  deleteEntry: (id: number) => request<{ ok: boolean }>('DELETE', `/plan/${id}`),

  grocery: () => request<GroceryItem[]>('GET', '/grocery'),
  generateGrocery: (start: string, end: string, include_queue: boolean) =>
    request<GroceryItem[]>('POST', '/grocery/generate', { start, end, include_queue }),
  addGrocery: (item: { name: string; amount?: string; aisle?: string }) =>
    request<GroceryItem>('POST', '/grocery', item),
  updateGrocery: (id: number, patch: Partial<Pick<GroceryItem, 'name' | 'amount' | 'aisle' | 'checked'>>) =>
    request<GroceryItem>('PATCH', `/grocery/${id}`, patch),
  deleteGrocery: (id: number) => request<{ ok: boolean }>('DELETE', `/grocery/${id}`),
  clearGrocery: (checkedOnly: boolean) => request<{ ok: boolean }>('DELETE', `/grocery?${qs({ checked_only: checkedOnly })}`),
}
