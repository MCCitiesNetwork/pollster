import named from './fixtures/named.json';
import { loadFromLocation } from './fetchSnapshot';

function loc(hash: string, search = ''): Location {
  return { hash, search } as Location;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

describe('loadFromLocation', () => {
  it('returns no-key when the fragment is empty', async () => {
    const fetchFn = vi.fn();
    expect(await loadFromLocation(loc(''), fetchFn)).toEqual({ state: 'no-key' });
    expect(await loadFromLocation(loc('#'), fetchFn)).toEqual({ state: 'no-key' });
    expect(fetchFn).not.toHaveBeenCalled();
  });

  it('returns ok with the snapshot for a valid paste', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse(named));
    const result = await loadFromLocation(loc('#abc123'), fetchFn);
    expect(result.state).toBe('ok');
    expect(fetchFn).toHaveBeenCalledWith('https://bytebin.lucko.me/abc123');
  });

  it('returns not-found on 404', async () => {
    const fetchFn = vi.fn().mockResolvedValue(new Response('', { status: 404 }));
    expect(await loadFromLocation(loc('#abc'), fetchFn)).toEqual({ state: 'not-found' });
  });

  it('returns network when fetch throws', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new Error('offline'));
    expect(await loadFromLocation(loc('#abc'), fetchFn)).toEqual({
      state: 'network',
      message: 'offline',
    });
  });

  it('returns network for other non-2xx statuses', async () => {
    const fetchFn = vi.fn().mockResolvedValue(new Response('', { status: 500 }));
    const result = await loadFromLocation(loc('#abc'), fetchFn);
    expect(result.state).toBe('network');
  });

  it('uses the paste query parameter instead of the default base', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse(named));
    await loadFromLocation(loc('#abc', '?paste=https://paste.example.com/'), fetchFn);
    expect(fetchFn).toHaveBeenCalledWith('https://paste.example.com/abc');
  });

  it('returns unsupported-format for a newer format', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse({ ...named, format: 2 }));
    expect(await loadFromLocation(loc('#abc'), fetchFn)).toEqual({
      state: 'unsupported-format',
      format: 2,
    });
  });

  it('returns malformed for invalid JSON', async () => {
    const fetchFn = vi.fn().mockResolvedValue(new Response('not json', { status: 200 }));
    expect(await loadFromLocation(loc('#abc'), fetchFn)).toEqual({ state: 'malformed' });
  });
});
