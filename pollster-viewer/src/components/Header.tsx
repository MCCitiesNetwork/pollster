import type { Snapshot } from '../snapshot';
import { SummaryTiles } from './SummaryTiles';
import { ThemeToggle } from './ThemeToggle';

interface Props {
  snapshot: Snapshot;
  onDownload: () => void;
}

function formatDate(iso: string | null): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d.toISOString().slice(0, 10);
}

export function Header({ snapshot, onDownload }: Props) {
  const { survey, totals } = snapshot;
  const opened = formatDate(survey.openedAt);
  const closed = formatDate(survey.closedAt);

  return (
    <header className="header">
      <div className="toolbar">
        <button type="button" className="btn" onClick={onDownload}>
          Download JSON
        </button>
        <ThemeToggle />
      </div>
      <h1>{survey.name}</h1>
      {survey.anonymous && <span className="badge">Anonymous survey</span>}
      {survey.description && <p className="description">{survey.description}</p>}
      {opened && (
        <p className="meta">
          {closed ? `Ran ${opened} to ${closed}` : `Open since ${opened}`}
        </p>
      )}
      <SummaryTiles participants={totals.participants} averageSeconds={totals.averageSeconds} />
    </header>
  );
}
