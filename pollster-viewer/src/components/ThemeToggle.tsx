import { useState } from 'react';

type Theme = 'light' | 'dark';

const STORAGE_KEY = 'pollster-theme';

function readStored(): Theme | null {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return v === 'light' || v === 'dark' ? v : null;
  } catch {
    return null;
  }
}

function systemTheme(): Theme {
  try {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

// Applies a remembered choice before first paint of the app; without one the
// CSS follows prefers-color-scheme on its own.
export function applyStoredTheme(): void {
  const stored = readStored();
  if (stored) document.documentElement.dataset.theme = stored;
}

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(() => readStored() ?? systemTheme());
  const next: Theme = theme === 'dark' ? 'light' : 'dark';

  function toggle() {
    document.documentElement.dataset.theme = next;
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // The choice still applies for this visit.
    }
    setTheme(next);
  }

  return (
    <button type="button" className="btn" onClick={toggle} aria-label={`Switch to ${next} theme`}>
      {theme === 'dark' ? 'Light' : 'Dark'}
    </button>
  );
}
