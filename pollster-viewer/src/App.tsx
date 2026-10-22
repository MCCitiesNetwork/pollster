import { useEffect, useState } from 'react';
import { loadFromLocation, type LoadResult } from './fetchSnapshot';
import { ChoiceResult } from './components/ChoiceResult';
import { ErrorScreen } from './components/ErrorScreen';
import { Header } from './components/Header';
import { TextAnswers } from './components/TextAnswers';

interface Props {
  // Injected so tests can supply results without a network.
  load?: () => Promise<LoadResult>;
}

function downloadJson(raw: unknown, name: string) {
  const blob = new Blob([JSON.stringify(raw, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${name}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

export function App({ load = () => loadFromLocation(window.location) }: Props) {
  const [result, setResult] = useState<LoadResult | null>(null);

  useEffect(() => {
    let cancelled = false;
    void load().then((r) => {
      if (!cancelled) setResult(r);
    });
    return () => {
      cancelled = true;
    };
    // load is intentionally read once per mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!result) {
    return (
      <main className="page">
        <p className="meta" role="status">
          Loading report...
        </p>
      </main>
    );
  }
  if (result.state !== 'ok') return <ErrorScreen failure={result} />;

  const { snapshot, raw } = result;
  const participants = snapshot.totals.participants;

  return (
    <div className="page">
      <Header snapshot={snapshot} onDownload={() => downloadJson(raw, snapshot.survey.id)} />
      <main>
        {snapshot.questions.map((q) =>
          q.type === 'TEXT' ? (
            <TextAnswers
              key={q.key}
              question={q}
              participants={participants}
              anonymous={snapshot.survey.anonymous}
            />
          ) : (
            <ChoiceResult key={q.key} question={q} participants={participants} />
          ),
        )}
      </main>
      <footer className="footer">Generated {snapshot.generatedAt}</footer>
    </div>
  );
}
