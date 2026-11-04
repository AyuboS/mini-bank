import { useState } from 'react';
import { Moon, Sun } from 'lucide-react';
import { applyTheme, currentTheme, type Theme } from '../theme';

/** Flips between light and dark, and says which one it will give you next. */
export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(() => currentTheme());

  const flip = () => {
    const next: Theme = theme === 'dark' ? 'light' : 'dark';
    applyTheme(next);
    setTheme(next);
  };

  return (
    <button
      type="button"
      className="icon-btn"
      onClick={flip}
      aria-label={theme === 'dark' ? 'Switch to the light theme' : 'Switch to the dark theme'}
      title={theme === 'dark' ? 'Light theme' : 'Dark theme'}
    >
      {theme === 'dark' ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
    </button>
  );
}
