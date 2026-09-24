import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { api, type ChatAnswer, type Citation } from '../api';

interface Turn {
  id: number;
  question: string;
  answer?: ChatAnswer;
  error?: string;
}

// One question per sample document in sample-docs/.
const EXAMPLES = [
  'How many vacation days do I get in my first year?',
  'How do I connect to the VPN from home?',
  'What is the maximum I can expense for a client dinner?',
];

const MARKER = /(\[\d{1,2}\])/g;

export default function AskView() {
  const [turns, setTurns] = useState<Turn[]>([]);
  const [draft, setDraft] = useState('');
  const [pending, setPending] = useState(false);
  const [activeTurnId, setActiveTurnId] = useState<number | null>(null);
  const [activeCitation, setActiveCitation] = useState<number | null>(null);
  const nextId = useRef(1);
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [turns]);

  async function ask(question: string) {
    const text = question.trim();
    if (!text || pending) return;
    const id = nextId.current++;
    setTurns((prev) => [...prev, { id, question: text }]);
    setDraft('');
    setPending(true);
    try {
      const answer = await api.ask(text);
      setTurns((prev) => prev.map((turn) => (turn.id === id ? { ...turn, answer } : turn)));
      setActiveTurnId(id);
      setActiveCitation(null);
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      setTurns((prev) => prev.map((turn) => (turn.id === id ? { ...turn, error: message } : turn)));
    } finally {
      setPending(false);
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    void ask(draft);
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      void ask(draft);
    }
  }

  function focusCitation(turnId: number, index: number) {
    setActiveTurnId(turnId);
    setActiveCitation(index);
    document.getElementById(`source-${index}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  const activeTurn = turns.find((turn) => turn.id === activeTurnId);

  return (
    <div className="ask">
      <section className="conversation" aria-live="polite">
        {turns.length === 0 && (
          <div className="intro">
            <h1>Ask about policies, tools and procedures</h1>
            <p>Answers come only from your company's documents, with a link to every source.</p>
            <p className="intro-label">Try one of these</p>
            <ul className="examples">
              {EXAMPLES.map((example) => (
                <li key={example}>
                  <button type="button" onClick={() => void ask(example)}>
                    {example}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}

        {turns.map((turn) => (
          <article key={turn.id} className="turn" aria-current={turn.id === activeTurnId ? 'true' : undefined}>
            <p className="question">{turn.question}</p>

            {!turn.answer && !turn.error && <p className="pending">Finding the answer…</p>}

            {turn.error && (
              <p className="error" role="alert">
                The question could not be answered: {turn.error}
              </p>
            )}

            {turn.answer && (
              <div className="answer">
                {turn.answer.answer.split(/\n+/).map((paragraph, p) => (
                  <p key={p}>
                    {paragraph.split(MARKER).map((part, i) => {
                      const match = part.match(/^\[(\d{1,2})\]$/);
                      if (!match) return part;
                      const index = Number(match[1]);
                      if (!turn.answer!.citations.some((c) => c.index === index)) return null;
                      const active = turn.id === activeTurnId && activeCitation === index;
                      return (
                        <button
                          key={i}
                          type="button"
                          className="marker"
                          aria-pressed={active}
                          onClick={() => focusCitation(turn.id, index)}
                        >
                          {index}
                        </button>
                      );
                    })}
                  </p>
                ))}
                <p className="meta">
                  Answered in {(turn.answer.latencyMs / 1000).toFixed(1)} s
                  {turn.answer.citations.length > 0 && turn.id !== activeTurnId && (
                    <button type="button" className="link" onClick={() => setActiveTurnId(turn.id)}>
                      Show sources for this answer
                    </button>
                  )}
                </p>
              </div>
            )}
          </article>
        ))}
        <div ref={endRef} />
      </section>

      <aside className="sources" aria-label="Sources">
        <h2>Sources</h2>
        <SourceList turn={activeTurn} activeCitation={activeCitation} onSelect={setActiveCitation} />
      </aside>

      <form className="composer" onSubmit={onSubmit}>
        <textarea
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={onKeyDown}
          placeholder="Ask a question…"
          aria-label="Ask a question"
          rows={2}
          maxLength={2000}
        />
        <button type="submit" className="primary" disabled={pending || !draft.trim()}>
          Ask
        </button>
      </form>
    </div>
  );
}

function SourceList({
  turn,
  activeCitation,
  onSelect,
}: {
  turn?: Turn;
  activeCitation: number | null;
  onSelect: (index: number) => void;
}) {
  if (!turn?.answer) return <p className="muted">Sources for the selected answer appear here.</p>;
  if (turn.answer.citations.length === 0) {
    return <p className="muted">No source in the knowledge base covers this question.</p>;
  }

  return (
    <ol className="source-list">
      {turn.answer.citations.map((citation: Citation) => (
        <li key={citation.index} id={`source-${citation.index}`}>
          <button
            type="button"
            className="source"
            aria-pressed={activeCitation === citation.index}
            onClick={() => onSelect(citation.index)}
          >
            <span className="source-index">{citation.index}</span>
            <span className="source-body">
              {citation.section && <span className="source-section">{citation.section}</span>}
              <span className="source-file">
                {citation.fileName}
                {citation.page != null && <span className="muted">, page {citation.page}</span>}
              </span>
              <span className="source-snippet">{citation.snippet}</span>
            </span>
          </button>
        </li>
      ))}
    </ol>
  );
}
