export const SUPPORTED_FORMAT = 1;

export interface SnapshotOption {
  key: string;
  label: string;
  votes: number;
}

export interface TextAnswer {
  text: string;
  player: string | null;
}

interface QuestionBase {
  key: string;
  text: string;
  shownTo: number;
  skipped: number;
}

export type SnapshotQuestion =
  | (QuestionBase & { type: 'SINGLE' | 'YES_NO'; options: SnapshotOption[] })
  | (QuestionBase & {
      type: 'MULTIPLE';
      options: SnapshotOption[];
      selectionsPerRespondent: Record<string, number>;
    })
  | (QuestionBase & { type: 'TEXT'; answers: TextAnswer[] });

export interface Snapshot {
  format: number;
  generatedAt: string;
  survey: {
    id: string;
    name: string;
    description: string;
    anonymous: boolean;
    openedAt: string | null;
    closedAt: string | null;
  };
  totals: { participants: number; averageSeconds: number };
  questions: SnapshotQuestion[];
}

export type ParseResult =
  | { ok: true; snapshot: Snapshot }
  | { ok: false; error: 'unsupported-format' | 'malformed'; format?: number };

const MALFORMED: ParseResult = { ok: false, error: 'malformed' };

type Obj = Record<string, unknown>;

function isObj(v: unknown): v is Obj {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function isNum(v: unknown): v is number {
  return typeof v === 'number' && Number.isFinite(v);
}

function isStr(v: unknown): v is string {
  return typeof v === 'string';
}

function parseOptions(raw: unknown): SnapshotOption[] | null {
  if (!Array.isArray(raw)) return null;
  const options: SnapshotOption[] = [];
  for (const o of raw) {
    if (!isObj(o) || !isStr(o.key) || !isStr(o.label) || !isNum(o.votes)) return null;
    options.push({ key: o.key, label: o.label, votes: o.votes });
  }
  return options;
}

function parseQuestion(raw: unknown): SnapshotQuestion | null {
  if (!isObj(raw) || !isStr(raw.key) || !isStr(raw.text) || !isNum(raw.shownTo)) return null;
  // Older writers may omit skipped; absent means nobody skipped.
  const skipped = raw.skipped === undefined ? 0 : raw.skipped;
  if (!isNum(skipped)) return null;
  const base = { key: raw.key, text: raw.text, shownTo: raw.shownTo, skipped };

  switch (raw.type) {
    case 'SINGLE':
    case 'YES_NO': {
      const options = parseOptions(raw.options);
      return options ? { ...base, type: raw.type, options } : null;
    }
    case 'MULTIPLE': {
      const options = parseOptions(raw.options);
      if (!options || !isObj(raw.selectionsPerRespondent)) return null;
      const selections: Record<string, number> = {};
      for (const [k, v] of Object.entries(raw.selectionsPerRespondent)) {
        if (!isNum(v)) return null;
        selections[k] = v;
      }
      return { ...base, type: 'MULTIPLE', options, selectionsPerRespondent: selections };
    }
    case 'TEXT': {
      if (!Array.isArray(raw.answers)) return null;
      const answers: TextAnswer[] = [];
      for (const a of raw.answers) {
        if (!isObj(a) || !isStr(a.text)) return null;
        if (a.player !== null && a.player !== undefined && !isStr(a.player)) return null;
        answers.push({ text: a.text, player: a.player ?? null });
      }
      return { ...base, type: 'TEXT', answers };
    }
    default:
      return null;
  }
}

export function parseSnapshot(raw: unknown): ParseResult {
  if (!isObj(raw) || !isNum(raw.format)) return MALFORMED;
  if (raw.format !== SUPPORTED_FORMAT) {
    return { ok: false, error: 'unsupported-format', format: raw.format };
  }
  const { survey, totals, questions } = raw;
  if (!isObj(survey) || !isStr(survey.id) || !isStr(survey.name)) return MALFORMED;
  if (!isObj(totals) || !isNum(totals.participants)) return MALFORMED;
  if (!Array.isArray(questions)) return MALFORMED;

  const parsed: SnapshotQuestion[] = [];
  for (const q of questions) {
    const question = parseQuestion(q);
    if (!question) return MALFORMED;
    parsed.push(question);
  }

  return {
    ok: true,
    snapshot: {
      format: raw.format,
      generatedAt: isStr(raw.generatedAt) ? raw.generatedAt : '',
      survey: {
        id: survey.id,
        name: survey.name,
        description: isStr(survey.description) ? survey.description : '',
        anonymous: survey.anonymous === true,
        openedAt: isStr(survey.openedAt) ? survey.openedAt : null,
        closedAt: isStr(survey.closedAt) ? survey.closedAt : null,
      },
      totals: {
        participants: totals.participants,
        averageSeconds: isNum(totals.averageSeconds) ? totals.averageSeconds : 0,
      },
      questions: parsed,
    },
  };
}
