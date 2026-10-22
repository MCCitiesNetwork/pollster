interface Props {
  participants: number;
  averageSeconds: number;
}

export function formatDuration(seconds: number): string {
  const total = Math.round(seconds);
  if (total < 60) return `${total}s`;
  const m = Math.floor(total / 60);
  const s = total % 60;
  return s === 0 ? `${m}m` : `${m}m ${s}s`;
}

export function SummaryTiles({ participants, averageSeconds }: Props) {
  return (
    <div className="tiles">
      <div className="tile">
        <span className="tile-label">Participants</span>
        <span className="tile-value">{participants}</span>
      </div>
      <div className="tile">
        <span className="tile-label">Average time</span>
        <span className="tile-value">{formatDuration(averageSeconds)}</span>
      </div>
    </div>
  );
}
