import type { SnapshotQuestion } from '../snapshot';
import { MultipleDistribution } from './MultipleDistribution';
import { QuestionMeta } from './QuestionMeta';

type ChoiceQuestion = Extract<SnapshotQuestion, { options: unknown }>;

interface Props {
  question: ChoiceQuestion;
  participants: number;
}

export function percent(part: number, whole: number): number {
  return whole > 0 ? Math.round((part / whole) * 100) : 0;
}

export function ChoiceResult({ question, participants }: Props) {
  // Percentages are of people who answered, so skips do not shrink every bar.
  const answered = question.shownTo - question.skipped;
  const options = [...question.options].sort((a, b) => b.votes - a.votes);
  const max = options.reduce((m, o) => Math.max(m, o.votes), 0);

  return (
    <section className="card">
      <h2>{question.text}</h2>
      <QuestionMeta
        shownTo={question.shownTo}
        participants={participants}
        skipped={question.skipped}
      />
      <ul className="bars">
        {options.map((o) => (
          <li key={o.key} className="bar-row">
            <div className="bar-head">
              <span className="bar-label">{o.label}</span>
              <span className="bar-value">
                {o.votes} <span className="pct">{percent(o.votes, answered)}%</span>
              </span>
            </div>
            <div className="bar-track" aria-hidden="true">
              <div className="bar-fill" style={{ width: `${max > 0 ? (o.votes / max) * 100 : 0}%` }} />
            </div>
          </li>
        ))}
      </ul>
      {question.type === 'MULTIPLE' && (
        <MultipleDistribution selections={question.selectionsPerRespondent} />
      )}
    </section>
  );
}
