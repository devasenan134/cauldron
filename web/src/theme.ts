// The light/dark theme: a setting on the account ("system", "light" or "dark"), shared with the app.
// It goes on <html data-theme>; "system" leaves it off so the device decides (index.css).
// A copy in localStorage lets index.html apply it before the page draws.

export type Theme = 'system' | 'light' | 'dark'

export function applyTheme(theme: Theme) {
  const root = document.documentElement
  if (theme === 'system') delete root.dataset.theme
  else root.dataset.theme = theme
  try {
    localStorage.setItem('theme', theme)
  } catch {
    // private window: the theme still applies, it just isn't remembered for the first paint
  }
}

/** Whether the page is showing dark right now. */
export const isDark = () =>
  document.documentElement.dataset.theme === 'dark' ||
  (!document.documentElement.dataset.theme && window.matchMedia('(prefers-color-scheme: dark)').matches)
