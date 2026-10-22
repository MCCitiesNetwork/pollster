import { parseSnapshot, type Snapshot } from './snapshot';

export type LoadResult =
  // raw is the parsed JSON as fetched, kept so Download JSON offers it unchanged.
  | { state: 'ok'; snapshot: Snapshot; raw: unknown }
  | { state: 'no-key' }
  | { state: 'not-found' }
  | { state: 'network'; message: string }
  | { state: 'unsupported-format'; format: number }
  | { state: 'malformed' };

const DEFAULT_PASTE_URL = 'https://bytebin.lucko.me';

function pasteBase(search: string): string {
  const override = new URLSearchParams(search).get('paste');
  const base = override || import.meta.env.VITE_PASTE_URL || DEFAULT_PASTE_URL;
  return base.replace(/\/+$/, '');
}

export async function loadFromLocation(
  loc: Pick<Location, 'hash' | 'search'>,
  fetchFn: typeof fetch = (input, init) => fetch(input, init),
): Promise<LoadResult> {
  const key = loc.hash.replace(/^#/, '').trim();
  if (!key) return { state: 'no-key' };

  // The key goes into a URL path, so it must be encoded.
  const url = `${pasteBase(loc.search)}/${encodeURIComponent(key)}`;

  let response: Response;
  try {
    response = await fetchFn(url);
  } catch (e) {
    return { state: 'network', message: e instanceof Error ? e.message : String(e) };
  }
  if (response.status === 404) return { state: 'not-found' };
  if (!response.ok) return { state: 'network', message: `Paste service answered ${response.status}` };

  let raw: unknown;
  try {
    raw = await response.json();
  } catch {
    return { state: 'malformed' };
  }

  const parsed = parseSnapshot(raw);
  if (parsed.ok) return { state: 'ok', snapshot: parsed.snapshot, raw };
  if (parsed.error === 'unsupported-format') {
    return { state: 'unsupported-format', format: parsed.format ?? 0 };
  }
  return { state: 'malformed' };
}
