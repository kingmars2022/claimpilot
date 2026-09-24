import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { api, type Citation, type Conversation, type ConversationSummary } from '../api';

interface Answer {
  text: string;
  grounded: boolean;
  citations: Citation[];
  /** Null for answers loaded from history. */
  latencyMs: number | null;
}

interface Turn {
  id: number;
  question: string;
  answer?: Answer;
  error?: string;
}

// One question per sample document in sample-docs/.
const EXAMPLES = [
  'How many vacation days do I get in my first year?',
  'How do I connect to the VPN from home?',
  'What is the maximum I can expense for a client dinner?',
];

const MARKER = /(\[\d{1,2}\])/g;

/** Rebuilds question/answer turns from a stored conversation. */
function toTurns(conversation: Conversation, firstId: number): Turn[] {
  const turns: Turn[] = [];
  for (let i = 0; i + 1 < conversation.messages.length; i += 2) {
    const question = conversation.messages[i];
    const answer = conversation.messages[i + 1];
    turns.push({
      id: firstId + turns.length,
      question: question.content,
      answer: {
        text: answer.content,
        grounded: answer.grounded ?? false,
        citations: answer.citations ?? [],
        latencyMs: null,
      },
    });
  }
  return turns;
}

export default function AskView() {
  const [history, setHistory] = useState<ConversationSummary[]>([]);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [turns, setTurns] = useState<Turn[]>([]);
  const [draft, setDraft] = useState('');
  const [pending, setPending] = useState(false);
  const [activeTurnId, setActiveTurnId] = useState<number | null>(null);
  const [activeCitation, setActiveCitation] = useState<number | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const nextId = useRef(1);
  const endRef = useRef<HTMLDivElement>(null);

  const refreshHistory = useCallback(async () => {
    try {
      setHistory(await api.conversations());
      setHistoryError(null);
    } catch (err) {
      setHistoryError(err instanceof Error ? err.message : String(err));
    }
  }, []);

  useEffect(() => {
    void refreshHistory();
  }, [refreshHistory]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [turns]);

  function startNewChat() {
    setConversationId(null);
    setTurns([]);
    setActiveTurnId(null);
    setActiveCitation(null);
    setHistoryOpen(false);
  }

  async function openConversation(id: string) {
    setHistoryOpen(false);
    try {
      const conversation = await api.conversation(id);
      const loaded = toTurns(conversation, nextId.current);
      nextId.current += loaded.length;
      setConversationId(conversation.id);
      setTurns(loaded);
      setActiveTurnId(loaded.at(-1)?.id ?? null);
      setActiveCitation(null);
    } catch (err) {
      setHistoryError(err instanceof Error ? err.message : String(err));
    }
  }

  async function removeConversation(id: string) {
    if (!window.confirm('Delete this conversation?')) return;
    try {
      await api.deleteConversation(id);
      if (id === conversationId) startNewChat();
      await refreshHistory();
    } catch (err) {
      setHistoryError(err instanceof Error ? err.message : String(err));
    }
  }

  async function ask(question: string) {
    const text = question.trim();
    if (!text || pending) return;
    const id = nextId.current++;
    setTurns((prev) => [...prev, { id, question: text }]);
    setDraft('');
    setPending(true);
    try {
      const response = await api.ask(text, conversationId);
      const answer: Answer = {
        text: response.answer,
        grounded: response.grounded,
        citations: response.citations,
        latencyMs: response.latencyMs,
      };
      setTurns((prev) => prev.map((turn) => (turn.id === id ? { ...turn, answer } : turn)));
      setConversationId(response.conversationId);
      setActiveTurnId(id);
      setActiveCitation(null);
      void refreshHistory();
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
      <aside className="history" data-open={historyOpen || undefined} aria-label="Conversation history">
        <div className="history-head">
          <button type="button" className="new-chat" onClick={startNewChat}>
            New conversation
          </button>
          <button
            type="button"
            className="quiet history-toggle"
            aria-expanded={historyOpen}
            onClick={() => setHistoryOpen((open) => !open)}
          >
            History ({history.length})
          </button>
        </div>
        {historyError && <p className="error small">{historyError}</p>}
        {history.length === 0 ? (
          <p className="small muted history-empty">Your past conversations appear here.</p>
        ) : (
          <ul className="history-list">
            {history.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  className="history-item"
                  aria-current={item.id === conversationId ? 'true' : undefined}
                  onClick={() => void openConversation(item.id)}
                  title={item.title}
                >
                  {item.title}
                </button>
                <button
                  type="button"
                  className="quiet history-delete"
                  aria-label={`Delete conversation: ${item.title}`}
                  onClick={() => void removeConversation(item.id)}
                >
                  ×
                </button>
              </li>
            ))}
          </ul>
        )}
      </aside>

      <section className="conversation" aria-live="polite">
        {turns.length === 0 && (
          <div className="intro">
            <h1>Ask about policies, tools and procedures</h1>
            <p>
              Answers come only from documents you are allowed to see, with a link to every source. Follow-up
              questions keep the context of the conversation.
            </p>
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
                {turn.answer.text.split(/\n+/).map((paragraph, p) => (
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
                  {turn.answer.latencyMs !== null && (
                    <span>Answered in {(turn.answer.latencyMs / 1000).toFixed(1)} s</span>
                  )}
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
          placeholder={conversationId ? 'Ask a follow-up…' : 'Ask a question…'}
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
    return <p className="muted">No document you can access covers this question.</p>;
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
