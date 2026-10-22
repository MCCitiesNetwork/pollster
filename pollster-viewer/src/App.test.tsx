import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import named from './fixtures/named.json';
import anonymous from './fixtures/anonymous.json';
import { App } from './App';
import type { LoadResult } from './fetchSnapshot';
import { parseSnapshot } from './snapshot';

function ok(raw: unknown): LoadResult {
  const parsed = parseSnapshot(raw);
  if (!parsed.ok) throw new Error('fixture invalid');
  return { state: 'ok', snapshot: parsed.snapshot, raw };
}

async function renderApp(result: LoadResult) {
  render(<App load={() => Promise.resolve(result)} />);
  // The first paint is the loading state; wait for the result.
  await screen.findByRole('banner').catch(() => screen.findByRole('alert'));
}

function questionSection(title: string): HTMLElement {
  const heading = screen.getByRole('heading', { name: title });
  const section = heading.closest('section');
  if (!section) throw new Error('no section for ' + title);
  return section;
}

describe('App', () => {
  it('renders survey name, description and participants', async () => {
    await renderApp(ok(named));
    expect(screen.getByRole('heading', { level: 1, name: 'City Priorities 2026' })).toBeInTheDocument();
    expect(screen.getByText(/Help shape what the city builds next/)).toBeInTheDocument();
    const tile = screen.getByText('Participants').closest('div');
    expect(tile).toHaveTextContent('52');
  });

  it('shows Anonymous badge only for anonymous fixture', async () => {
    const { unmount } = render(<App load={() => Promise.resolve(ok(anonymous))} />);
    expect(await screen.findByText('Anonymous survey')).toBeInTheDocument();
    unmount();
    await renderApp(ok(named));
    expect(screen.queryByText('Anonymous survey')).not.toBeInTheDocument();
  });

  it('sorts options by votes and shows percent', async () => {
    await renderApp(ok(named));
    const section = questionSection('Which tree should be the city symbol?');
    const items = within(section).getAllByRole('listitem');
    expect(items).toHaveLength(12);
    // Dogwood has the most votes (11 of 52), Larch the fewest (0).
    expect(items[0]).toHaveTextContent('Dogwood');
    expect(items[0]).toHaveTextContent('11');
    expect(items[0]).toHaveTextContent('21%');
    expect(items[1]).toHaveTextContent('Birch');
    expect(items[11]).toHaveTextContent('Larch');
  });

  it('uses answered respondents as the percent base for skippable questions', async () => {
    await renderApp(ok(named));
    const section = questionSection('How do you rate the market? (optional)');
    // 24 of the 43 who answered.
    expect(within(section).getAllByRole('listitem')[0]).toHaveTextContent('56%');
    expect(section).toHaveTextContent('9 skipped');
  });

  it('shows the selections distribution for multiple choice', async () => {
    await renderApp(ok(named));
    const section = questionSection('Which issues matter most? Pick up to three.');
    expect(within(section).getByText('Selections per respondent')).toBeInTheDocument();
    expect(within(section).getByText('2 selections')).toBeInTheDocument();
  });

  it('shows "Shown to 30 of 52" for a conditional question', async () => {
    await renderApp(ok(named));
    const section = questionSection('Anything else you want the council to know?');
    expect(within(section).getByText(/Shown to 30 of 52/)).toBeInTheDocument();
  });

  it('text search filters answers', async () => {
    const user = userEvent.setup();
    await renderApp(ok(named));
    const section = questionSection('Anything else you want the council to know?');
    await user.type(within(section).getByRole('searchbox'), 'tram');
    const items = within(section).getAllByRole('listitem');
    expect(items).toHaveLength(1);
    expect(items[0]).toHaveTextContent('A tram line would be great');
  });

  it('text answers paginate at 20', async () => {
    const user = userEvent.setup();
    await renderApp(ok(named));
    const section = questionSection('Anything else you want the council to know?');
    expect(within(section).getAllByRole('listitem')).toHaveLength(20);
    expect(within(section).getByText('Page 1 of 2')).toBeInTheDocument();
    await user.click(within(section).getByRole('button', { name: 'Next' }));
    expect(within(section).getAllByRole('listitem')).toHaveLength(8);
    expect(within(section).getByText('Page 2 of 2')).toBeInTheDocument();
  });

  it('shows player names for named surveys only', async () => {
    const { unmount } = render(<App load={() => Promise.resolve(ok(named))} />);
    await screen.findByRole('banner');
    expect(screen.getAllByText('Alex').length).toBeGreaterThan(0);
    unmount();
    render(<App load={() => Promise.resolve(ok(anonymous))} />);
    await screen.findByRole('banner');
    expect(screen.queryByText('Alex')).not.toBeInTheDocument();
  });

  it('renders error screens for no-key, not-found and unsupported-format', async () => {
    const { unmount: u1 } = render(<App load={() => Promise.resolve({ state: 'no-key' })} />);
    expect(await screen.findByRole('alert')).toHaveTextContent(/no report key/i);
    u1();
    const { unmount: u2 } = render(<App load={() => Promise.resolve({ state: 'not-found' })} />);
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be found/i);
    u2();
    render(<App load={() => Promise.resolve({ state: 'unsupported-format', format: 2 })} />);
    expect(await screen.findByRole('alert')).toHaveTextContent(/updating the viewer/i);
  });

  it('renders network and malformed error screens', async () => {
    const { unmount } = render(
      <App load={() => Promise.resolve({ state: 'network', message: 'offline' })} />,
    );
    expect(await screen.findByRole('alert')).toHaveTextContent('offline');
    unmount();
    render(<App load={() => Promise.resolve({ state: 'malformed' })} />);
    expect(await screen.findByRole('alert')).toHaveTextContent(/not a valid/i);
  });

  it('text answers render markup literally', async () => {
    const questions: unknown[] = [...named.questions];
    questions[2] = { ...named.questions[2], answers: [{ text: '<b>x</b>', player: null }] };
    const raw = { ...named, questions };
    await renderApp(ok(raw));
    const section = questionSection('Anything else you want the council to know?');
    expect(within(section).getByText('<b>x</b>')).toBeInTheDocument();
    expect(section.querySelector('b')).toBeNull();
  });

  it('download button offers the raw JSON', async () => {
    const user = userEvent.setup();
    let blob: Blob | undefined;
    URL.createObjectURL = vi.fn((b: Blob | MediaSource) => {
      blob = b as Blob;
      return 'blob:test';
    });
    URL.revokeObjectURL = vi.fn();
    const clicked = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    await renderApp(ok(named));
    await user.click(screen.getByRole('button', { name: 'Download JSON' }));

    expect(clicked).toHaveBeenCalled();
    expect(blob).toBeDefined();
    // jsdom's Blob has no text(), so read it through FileReader.
    const text = await new Promise<string>((resolve) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.readAsText(blob!);
    });
    expect(JSON.parse(text)).toEqual(named);
    clicked.mockRestore();
  });

  it('theme toggle remembers the choice', async () => {
    const user = userEvent.setup();
    await renderApp(ok(named));
    await user.click(screen.getByRole('button', { name: /switch to dark theme/i }));
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(localStorage.getItem('pollster-theme')).toBe('dark');
  });
});
