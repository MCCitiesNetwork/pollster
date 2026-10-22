import { useMemo, useState } from 'react';
import type { SnapshotQuestion } from '../snapshot';
import { QuestionMeta } from './QuestionMeta';

export const PAGE_SIZE = 20;

type TextQuestion = Extract<SnapshotQuestion, { type: 'TEXT' }>;

interface Props {
  question: TextQuestion;
  participants: number;
  anonymous: boolean;
}

export function TextAnswers({ question, participants, anonymous }: Props) {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return question.answers;
    return question.answers.filter(
      (a) =>
        a.text.toLowerCase().includes(needle) || (a.player ?? '').toLowerCase().includes(needle),
    );
  }, [query, question.answers]);

  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const current = Math.min(page, pages - 1);
  const visible = filtered.slice(current * PAGE_SIZE, (current + 1) * PAGE_SIZE);

  return (
    <section className="card">
      <h2>{question.text}</h2>
      <QuestionMeta
        shownTo={question.shownTo}
        participants={participants}
        skipped={question.skipped}
      />
      <input
        type="search"
        className="search"
        placeholder="Search answers"
        aria-label="Search answers"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setPage(0);
        }}
      />
      <p className="meta">
        {filtered.length} {filtered.length === 1 ? 'answer' : 'answers'}
      </p>
      {/* Answers are rendered as React text children, so markup stays literal. */}
      <ul className="answers">
        {visible.map((a, i) => (
          <li key={current * PAGE_SIZE + i}>
            <span className="answer-text">{a.text}</span>
            {!anonymous && a.player && <span className="answer-player">{a.player}</span>}
          </li>
        ))}
      </ul>
      {pages > 1 && (
        <nav className="pager" aria-label="Pagination">
          <button type="button" disabled={current === 0} onClick={() => setPage(current - 1)}>
            Previous
          </button>
          <span>
            Page {current + 1} of {pages}
          </span>
          <button
            type="button"
            disabled={current >= pages - 1}
            onClick={() => setPage(current + 1)}
          >
            Next
          </button>
        </nav>
      )}
    </section>
  );
}
