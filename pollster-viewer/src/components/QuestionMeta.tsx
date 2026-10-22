interface Props {
  shownTo: number;
  participants: number;
  skipped: number;
}

// Only worth showing when the question was conditional or could be skipped.
export function QuestionMeta({ shownTo, participants, skipped }: Props) {
  if (shownTo >= participants && skipped === 0) return null;
  return (
    <p className="meta">
      Shown to {shownTo} of {participants}
      {skipped > 0 ? ` · ${skipped} skipped` : ''}
    </p>
  );
}
