interface Props {
  selections: Record<string, number>;
}

export function MultipleDistribution({ selections }: Props) {
  const rows = Object.entries(selections)
    .map(([count, respondents]) => ({ count: Number(count), respondents }))
    .sort((a, b) => a.count - b.count);
  if (rows.length === 0) return null;
  const max = Math.max(...rows.map((r) => r.respondents));

  return (
    <div className="distribution">
      <h3>Selections per respondent</h3>
      <ul className="bars compact">
        {rows.map((r) => (
          <li key={r.count} className="bar-row">
            <div className="bar-head">
              <span className="bar-label">
                {r.count} {r.count === 1 ? 'selection' : 'selections'}
              </span>
              <span className="bar-value">{r.respondents} respondents</span>
            </div>
            <div className="bar-track" aria-hidden="true">
              <div
                className="bar-fill alt"
                style={{ width: `${max > 0 ? (r.respondents / max) * 100 : 0}%` }}
              />
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
