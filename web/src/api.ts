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
  /** A prepped ingredient (cooked rice, a sauce) that other recipes use by weight. */
  is_prep: boolean
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
  /** Counted with your own version of the food. */
  food_edited: boolean
  /** Made from this prepped-ingredient recipe instead of a food. */
  prep_id: number | null
  prep_title: string | null
  nutrition: Macros | null
}

export type Step = { id: number; position: number; title: string; text: string }

export type RecipeDetail = Omit<RecipeSummary, 'kcal_per_serving'> & {
  favorite: boolean
  folder_ids: number[]
  parent_id: number | null
  parent_title: string | null
  variations: { id: number; title: string }[]
  /** A prep: your recipes that use it. */
  used_in: { id: number; title: string }[]
  yield_grams: number | null
  can_edit: boolean
  slug: string
  source_url: string | null
  video_url: string | null
  author: string | null
  /** Who took the photo, when it isn't yours (the starter recipes' photos). */
  image_credit: { author: string; license: string; license_url: string | null; source_url: string | null } | null
  description: string
  yield_text: string | null
  notes: string
  source_nutrition: (Partial<Record<'calories' | 'protein' | 'fat' | 'carbohydrates', number>> & { per?: 'serving' | 'recipe'; from?: string }) | null
  ingredients: Ingredient[]
  steps: Step[]
  nutrition: {
    total: Macros
    per_serving: Macros | null
    left_out: string[]
    estimated: string[]
    grams: number
    /** A prep: what it weighs when done, and per 100 g. */
    yield_grams: number | null
    per_100g: Macros | null
  }
}

/** A food, per 100 g, as you see it (your own version when you've edited it). */
export type Food = {
  id: number; name: string; source: string; brand: string; notes: string
  kcal: number; protein: number; fat: number; carbs: number
  fiber: number | null; sugar: number | null; sodium_mg: number | null
  edited: boolean; own: boolean
}
export type FoodRow = Food & { recipes: number; names: string[] }
export type FoodDetail = FoodRow & {
  default: (Pick<Food, 'name' | 'kcal' | 'protein' | 'fat' | 'carbs' | 'fiber' | 'sugar' | 'sodium_mg'>) | null
  portions: { unit: string; grams: number }[]
  category: string | null
  used_in: { id: number; title: string }[]
}
export type FoodIn = Pick<Food, 'name' | 'brand' | 'notes' | 'kcal' | 'protein' | 'fat' | 'carbs' | 'fiber' | 'sugar' | 'sodium_mg'>
export type Unlinked = { name: string; count: number; recipes: { id: number; title: string }[] }
export type PrepStock = {
  entry_id: number; recipe_id: number; title: string; image_url: string | null; day: string | null
  made_grams: number; discarded: number
  /** In the fridge now (made, less meals before today and what was thrown out). */
  grams_now: number
  /** Spare once the planned meals have taken theirs. */
  grams_left: number
  kcal_per_100g: number | null
  uses: { entry_id: number; day: string | null; title: string; grams: number }[]
}

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
  cook_portions: number | null
  portions_left: number | null
  discarded: number
  leftover_of: number | null
  /** Makes a prepped ingredient (made_grams of it); nothing is eaten here. */
  is_prep: boolean
  made_grams: number | null
  grams_left: number | null
  /** Preps this meal needs that nothing planned covers (the grocery list buys their ingredients). */
  short: { prep_id: number; title: string; grams: number }[]
  meal: Meal
  /** Logged: eaten, or eaten out (what was planned went to the fridge). */
  status: 'eaten' | 'out' | null
  out_kcal: number | null
  /** What the log counts toward today's calories. */
  eaten_kcal: number | null
}

export const MEALS = ['breakfast', 'lunch', 'dinner'] as const
export type Meal = (typeof MEALS)[number]

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

export type Me = { email: string; name: string; is_owner: boolean; kcal_goal: number; theme: 'system' | 'light' | 'dark'; catalog_view: 'grid' | 'list' }

export type AppRelease = { version: string; notes: string; size: number }

export type Profile = {
  cooked: number; recipes_cooked: number; mine: number; favorites: number; streak: number
  days: Record<string, number>; out_days: string[]; cuisines: [string, number][]; categories: [string, number][]
}
export type Cooked = { day: string; entry_id: number; servings: number; batch: number | null; recipe: RecipeSummary }
export type FolderSummary = { id: number; name: string; count: number; covers: string[] }
export type Catalog = { mine: RecipeSummary[]; favorites: RecipeSummary[]; folders: FolderSummary[] }
export type TemplateItem = { name: string; amount: string; aisle: string }
export type GroceryTemplate = { id: number; name: string; items: TemplateItem[] }

export type FeedbackType = 'bug' | 'feature'
export type Feedback = {
  id: number; type: FeedbackType; title: string; body: string; meta: Record<string, string>
  status: 'open' | 'done'; created_at: string
  /** For the owner: who sent it. */
  user_name: string | null; user_email: string | null
}

export type ImportJob = { id: number; url: string; status: string; message: string; recipe_id: number | null }

export type TagGroup = { name: string; tags: string[] }
export type Facets = { cuisines: string[]; categories: string[]; tag_groups: TagGroup[] }

/** What the recipe list is filtered and sorted by (the same options as the app). */
export type RecipeFilter = {
  q: string
  cuisines: string[]
  categories: string[]
  tags: string[]
  maxMinutes: number | null
  kcal: 'light' | 'medium' | 'hearty' | null
  mine: boolean
  prep: boolean
  sort: 'title' | 'quickest' | 'lowest_kcal' | 'highest_protein' | 'newest'
}
export const KCAL_RANGES = { light: ['Under 400', null, 400], medium: ['400–700', 400, 700], hearty: ['Over 700', 700, null] } as const

/** A recipe of your own, as sent to the server. */
export type RecipeIn = {
  title: string
  description: string
  image_url: string | null
  video_url: string | null
  source_url: string | null
  servings: number | null
  yield_text: string | null
  total_minutes: number | null
  cuisine: string | null
  category: string | null
  tags: string[]
  notes: string
  is_prep: boolean
  yield_grams: number | null
  ingredients: { group: string | null; name: string; note: string; label: string; prep_id: number | null }[]
  steps: { title: string; text: string }[]
}

/** The shared libraries: read-only, and not anyone's own recipes. */
export const LIBRARY_SOURCES = ['cookwell', 'starter']

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
  /** Deletes the account and everything in it (not the owner's). */
  deleteAccount: () => request<{ ok: boolean }>('DELETE', '/auth/me'),
  setKcalGoal: (kcal_goal: number) => request<Me>('PATCH', '/auth/me', { kcal_goal }),
  setTheme: (theme: Me['theme']) => request<Me>('PATCH', '/auth/me', { theme }),
  setCatalogView: (catalog_view: Me['catalog_view']) => request<Me>('PATCH', '/auth/me', { catalog_view }),
  appLatest: () => request<AppRelease | null>('GET', '/app/latest'),

  recipesFiltered: (f: RecipeFilter) => {
    const p = new URLSearchParams()
    if (f.q.trim()) p.set('q', f.q.trim())
    p.set('sort', f.sort)
    if (f.mine) p.set('mine', 'true')
    if (f.prep) p.set('prep', 'true')
    if (f.maxMinutes) p.set('max_minutes', String(f.maxMinutes))
    if (f.kcal) {
      const [, min, max] = KCAL_RANGES[f.kcal]
      if (min != null) p.set('min_kcal', String(min))
      if (max != null) p.set('max_kcal', String(max))
    }
    f.cuisines.forEach((c) => p.append('cuisine', c))
    f.categories.forEach((c) => p.append('category', c))
    f.tags.forEach((t) => p.append('tag', t))
    return request<RecipeSummary[]>('GET', `/recipes?${p}`)
  },
  createRecipe: (r: RecipeIn) => request<RecipeDetail>('POST', '/recipes', r),
  updateRecipe: (id: number, r: RecipeIn) => request<RecipeDetail>('PUT', `/recipes/${id}`, r),
  deleteRecipe: (id: number) => request<{ ok: boolean }>('DELETE', `/recipes/${id}`),
  uploadImage: async (file: Blob): Promise<string> => {
    const res = await fetch('/api/images', { method: 'POST', headers: { 'content-type': file.type || 'image/jpeg' }, body: file })
    if (res.status === 401) throw new SignedOut()
    if (!res.ok) throw new Error(`Upload failed (${res.status}): ${await res.text()}`)
    return ((await res.json()) as { url: string }).url
  },
  profile: () => request<Profile>('GET', '/profile'),
  cooked: () => request<Cooked[]>('GET', '/cooked'),
  catalog: () => request<Catalog>('GET', '/catalog'),
  setFavorite: (id: number, on: boolean) => request<{ ok: boolean }>(on ? 'PUT' : 'DELETE', `/favorites/${id}`),
  createFolder: (name: string) => request<FolderSummary>('POST', '/folders', { name }),
  renameFolder: (id: number, name: string) => request<FolderSummary>('PATCH', `/folders/${id}`, { name }),
  deleteFolder: (id: number) => request<{ ok: boolean }>('DELETE', `/folders/${id}`),
  folder: (id: number) => request<{ id: number; name: string; recipes: RecipeSummary[] }>('GET', `/folders/${id}`),
  setInFolder: (folderId: number, recipeId: number, on: boolean) =>
    request<{ ok: boolean }>(on ? 'PUT' : 'DELETE', `/folders/${folderId}/recipes/${recipeId}`),
  /** Your copy of a recipe, saved as [body] (the editor's form). */
  makeVariation: (id: number, body?: RecipeIn) => request<{ id: number }>('POST', `/recipes/${id}/variation`, body),
  templates: () => request<GroceryTemplate[]>('GET', '/grocery/templates'),
  saveTemplate: (id: number | null, name: string, items: Partial<TemplateItem>[]) =>
    id == null ? request<GroceryTemplate>('POST', '/grocery/templates', { name, items }) : request<GroceryTemplate>('PUT', `/grocery/templates/${id}`, { name, items }),
  templateFromList: (name: string) => request<GroceryTemplate>('POST', '/grocery/templates/from-list', { name }),
  deleteTemplate: (id: number) => request<{ ok: boolean }>('DELETE', `/grocery/templates/${id}`),
  applyTemplate: (id: number) => request<GroceryItem[]>('POST', `/grocery/templates/${id}/apply`),
  feedback: () => request<Feedback[]>('GET', '/feedback'),
  sendFeedback: (f: { type: FeedbackType; title: string; body: string; meta: Record<string, string> }) =>
    request<Feedback>('POST', '/feedback', f),
  setFeedbackStatus: (id: number, status: Feedback['status']) => request<Feedback>('PATCH', `/feedback/${id}`, { status }),
  importStatus: () => request<{ ready: boolean; instagram_cookies: boolean }>('GET', '/import/status'),
  startImport: (url: string) => request<ImportJob>('POST', '/import', { url }),
  /** A PDF, a photo of a recipe, or a recipe file (YAML, JSON, text). */
  importFile: async (file: File): Promise<ImportJob> => {
    const res = await fetch(`/api/import/file?name=${encodeURIComponent(file.name)}`,
      { method: 'POST', headers: { 'content-type': file.type || 'application/octet-stream' }, body: file })
    if (res.status === 401) throw new SignedOut()
    if (!res.ok) throw new Error(`${res.status} ${await res.text()}`)
    return res.json() as Promise<ImportJob>
  },
  importJob: (id: number) => request<ImportJob>('GET', `/import/${id}`),
  appReleases: () => request<AppRelease[]>('GET', '/app/releases'),
  recipes: (p: { q?: string; cuisine?: string; category?: string } = {}) =>
    request<RecipeSummary[]>('GET', `/recipes?${qs(p)}`),
  facets: () => request<Facets>('GET', '/recipes/facets'),
  recipe: (id: number) => request<RecipeDetail>('GET', `/recipes/${id}`),
  patchIngredient: (id: number, patch: { grams?: number | null; food_id?: number | null; prep_id?: number | null }) =>
    request<RecipeDetail>('PATCH', `/ingredients/${id}`, patch),
  setPrep: (id: number, patch: { is_prep?: boolean; yield_grams?: number | null }) =>
    request<RecipeDetail>('PATCH', `/recipes/${id}/prep`, patch),
  preps: () => request<RecipeSummary[]>('GET', '/recipes?prep=true'),
  foods: (q: string) => request<Food[]>('GET', `/foods?${qs({ q })}`),
  foodLibrary: () => request<FoodRow[]>('GET', '/foods/library'),
  food: (id: number) => request<FoodDetail>('GET', `/foods/${id}`),
  saveFood: (id: number, f: FoodIn) => request<FoodDetail>('PUT', `/foods/${id}`, f),
  addFood: (f: FoodIn) => request<FoodDetail>('POST', '/foods', f),
  resetFood: (id: number) => request<{ ok: boolean }>('DELETE', `/foods/${id}`),
  unlinked: () => request<Unlinked[]>('GET', '/foods/unlinked'),
  assignFood: (id: number, name: string) => request<{ ingredients: number; recipes: number }>('POST', `/foods/${id}/assign`, { name }),
  prepStock: () => request<PrepStock[]>('GET', '/prep-stock'),

  plan: (start: string, days = 7) => request<Plan>('GET', `/plan?${qs({ start, days })}`),
  addEntry: (e: {
    day: string | null
    recipe_id?: number
    leftover_of?: number
    title?: string
    servings?: number
    cook_portions?: number | null
    made_grams?: number | null
    position?: number
    meal?: Meal
    status?: 'out'
    out_kcal?: number | null
  }) => request<PlanEntry>('POST', '/plan', e),
  updateEntry: (
    id: number,
    patch: {
      day?: string | null
      position?: number
      servings?: number
      title?: string
      cook_portions?: number | null
      discarded?: number
      made_grams?: number | null
      meal?: Meal
      status?: 'eaten' | 'out' | null
      out_kcal?: number | null
    },
  ) => request<PlanEntry>('PATCH', `/plan/${id}`, patch),
  batches: () => request<PlanEntry[]>('GET', '/batches'),
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
