import type { LoadResult } from '../fetchSnapshot';

type Failure = Exclude<LoadResult, { state: 'ok' }>;

function content(failure: Failure): { title: string; body: string } {
  switch (failure.state) {
    case 'no-key':
      return {
        title: 'No report key',
        body: 'This link has no report key. Open the link the plugin gave you in chat, including everything after the #.',
      };
    case 'not-found':
      return {
        title: 'Report not found',
        body: 'This report could not be found. The paste may have expired; publish the report again to get a new link.',
      };
    case 'network':
      return {
        title: 'Could not load the report',
        body: `The paste service could not be reached: ${failure.message}. Check your connection and reload.`,
      };
    case 'unsupported-format':
      return {
        title: 'Newer report format',
        body: `This report uses format ${failure.format}, which this viewer does not understand yet. Try reloading, or ask the site owner about updating the viewer.`,
      };
    case 'malformed':
      return {
        title: 'Unreadable report',
        body: 'The data at this link is not a valid Pollster report.',
      };
  }
}

export function ErrorScreen({ failure }: { failure: Failure }) {
  const { title, body } = content(failure);
  return (
    <main className="page">
      <div className="card error" role="alert">
        <h1>{title}</h1>
        <p>{body}</p>
      </div>
    </main>
  );
}
