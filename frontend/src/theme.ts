/**
 * Light and dark.
 *
 * The preference is read from storage, and from the operating system when
 * storage has nothing to say. The stored value is applied by a small script in
 * index.html before first paint, so this module never has to deal with a flash
 * of the wrong theme — it only ever reads back what that script decided, and
 * writes the customer's choice for next time.
 */
export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'minibank.theme';

/**
 * The theme in effect.
 *
 * Read off the document rather than recomputed, so this can never disagree with
 * what is actually on screen.
 */
export function currentTheme(): Theme {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
}

export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme;
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // Private browsing with storage denied: the theme still applies for this
    // session, it simply will not be remembered.
  }
}
