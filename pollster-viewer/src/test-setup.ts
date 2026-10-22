import '@testing-library/jest-dom/vitest';

// Newer Node versions define their own global localStorage that shadows jsdom's
// and is unusable without a flag, so tests get a plain in-memory store.
const store = new Map<string, string>();
Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: {
    getItem: (k: string) => store.get(k) ?? null,
    setItem: (k: string, v: string) => void store.set(k, String(v)),
    removeItem: (k: string) => void store.delete(k),
    clear: () => store.clear(),
  } satisfies Partial<Storage>,
});

afterEach(() => {
  store.clear();
  delete document.documentElement.dataset.theme;
});
