// Shared with the Android app (GroceryScreen.kt): aisle order, emoji, and quick-add parsing.

/** Roughly the order you walk a store in. */
const AISLE_ORDER = ['produce', 'bakery', 'bread', 'meat', 'seafood', 'dairy', 'eggs', 'grains', 'pantry', 'canned', 'oils',
  'condiments', 'spices', 'baking', 'snacks', 'beverages', 'drinks', 'frozen', 'other']

export function aisleRank(aisle: string): number {
  const i = AISLE_ORDER.findIndex((a) => aisle.toLowerCase().includes(a))
  return i < 0 ? AISLE_ORDER.length : i
}

const AISLE_EMOJI: [string, string][] = [
  ['produce', '🥬'], ['meat', '🥩'], ['seafood', '🐟'], ['dairy', '🥚'], ['eggs', '🥚'], ['bakery', '🍞'], ['bread', '🍞'],
  ['pantry', '🥫'], ['spices', '🧂'], ['frozen', '🧊'], ['beverages', '🥤'], ['drinks', '🥤'], ['condiments', '🫙'],
  ['oils', '🫒'], ['canned', '🥫'], ['grains', '🌾'], ['baking', '🧁'], ['snacks', '🍿'], ['other', '🛒'],
]

export const aisleEmoji = (aisle: string) => AISLE_EMOJI.find(([k]) => aisle.toLowerCase().includes(k))?.[1] ?? '🛒'

/** "Paneer 200 g" -> name + amount: a trailing number (and unit) is the amount. */
export function parseQuickAdd(text: string): { name: string; amount: string } {
  const m = text.trim().match(/^(.*?)\s+(\d[\d.,/]*\s*\p{L}{0,6})$/u)
  return m ? { name: m[1], amount: m[2] } : { name: text.trim(), amount: '' }
}
