import named from './fixtures/named.json';
import anonymous from './fixtures/anonymous.json';
import { parseSnapshot } from './snapshot';

describe('parseSnapshot', () => {
  it('parses named and anonymous fixtures', () => {
    const n = parseSnapshot(named);
    const a = parseSnapshot(anonymous);
    expect(n.ok).toBe(true);
    expect(a.ok).toBe(true);
    if (n.ok && a.ok) {
      expect(n.snapshot.survey.anonymous).toBe(false);
      expect(a.snapshot.survey.anonymous).toBe(true);
      expect(n.snapshot.questions.map((q) => q.type)).toEqual([
        'SINGLE',
        'MULTIPLE',
        'TEXT',
        'YES_NO',
        'SINGLE',
      ]);
    }
  });

  it('rejects format 2 as unsupported-format', () => {
    expect(parseSnapshot({ ...named, format: 2 })).toEqual({
      ok: false,
      error: 'unsupported-format',
      format: 2,
    });
  });

  it('rejects missing questions as malformed', () => {
    const { questions: _questions, ...rest } = named;
    expect(parseSnapshot(rest)).toEqual({ ok: false, error: 'malformed' });
  });

  it('rejects non-objects as malformed', () => {
    expect(parseSnapshot('nope')).toEqual({ ok: false, error: 'malformed' });
    expect(parseSnapshot(null)).toEqual({ ok: false, error: 'malformed' });
  });

  it('rejects an unknown question type as malformed', () => {
    const bad = { ...named, questions: [{ key: 'x', type: 'RANK', text: 't', shownTo: 1 }] };
    expect(parseSnapshot(bad)).toEqual({ ok: false, error: 'malformed' });
  });
});
