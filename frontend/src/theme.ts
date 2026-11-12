/** Light and dark. */
export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'minibank.theme';

/** The theme in effect. */
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
