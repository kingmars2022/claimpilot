import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import {
  api,
  type AnswerStatus,
  type CallKit,
  type Citation,
  type Conversation,
  type ConversationSummary,
} from '../api';
import { errorText, PageRef, useDocuments } from './shared';

interface Answer {
  text: string;
  status: AnswerStatus;
  citations: Citation[];
  callKit: CallKit | null;
  /** Null for answers loaded from history. */
  latencyMs: number | null;
}

interface Turn {
  id: number;
  question: string;
  answer?: Answer;
  error?: string;
}

// Written for the sample policies in sample-docs/; the second one is answered from a French policy.
const EXAMPLES = [
  'How much does my plan pay for physiotherapy each year?',
  'Est-ce que la massothérapie demande une recommandation médicale ?',
  '我的保险报销针灸吗？',
];

const MARKER = /(\[\d{1,2}\])/g;

function toTurns(conversation: Conversation, firstId: number): Turn[] {
  const turns: Turn[] = [];
  for (let i = 0; i + 1 < conversation.messages.length; i += 2) {
    const answer = conversation.messages[i + 1];
    turns.push({
      id: firstId + turns.length,
      question: conversation.messages[i].content,
      answer: {
        text: answer.content,
        status: answer.status ?? 'ANSWERED',
        citations: answer.citations ?? [],
        callKit: answer.callKit,
        latencyMs: null,
      },
    });
  }
  return turns;
}

export default function AskView() {
  const policies = useDocuments('POLICY');
  const [policyId, setPolicyId] = useState<string | null>(null);
  const [history, setHistory] = useState<ConversationSummary[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [turns, setTurns] = useState<Turn[]>([]);
  const [draft, setDraft] = useState('');
  const [pending, setPending] = useState(false);
  const [activeTurnId, setActiveTurnId] = useState<number | null>(null);
  const [activeCitation, setActiveCitation] = useState<number | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const nextId = useRef(1);
  const endRef = useRef<HTMLDivElement>(null);

  // Default to the most recent ready policy.
  useEffect(() => {
    if (!policyId && policies.ready.length > 0) setPolicyId(policies.ready[0].id);
  }, [policyId, policies.ready]);

  const refreshHistory = useCallback(async () => {
    try {
      setHistory(await api.conversations());
    } catch (err) {
      setError(errorText(err));
    }
  }, []);

  useEffect(() => {
    void refreshHistory();
  }, [refreshHistory]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [turns]);

  const policyName = (id: string) => policies.documents.find((p) => p.id === id)?.fileName ?? 'Deleted policy';

  function startNewChat(forPolicy = policyId) {
    setConversationId(null);
    setTurns([]);
    setActiveTurnId(null);
    setActiveCitation(null);
    setHistoryOpen(false);
    setPolicyId(forPolicy);
  }

  async function openConversation(id: string) {
    setHistoryOpen(false);
    try {
      const conversation = await api.conversation(id);
      const loaded = toTurns(conversation, nextId.current);
      nextId.current += loaded.length;
      setConversationId(conversation.id);
      setPolicyId(conversation.policyId);
      setTurns(loaded);
      setActiveTurnId(loaded.at(-1)?.id ?? null);
      setActiveCitation(null);
    } catch (err) {
      setError(errorText(err));
    }
  }

  async function removeConversation(id: string) {
    if (!window.confirm('Delete this conversation?')) return;
    try {
      await api.deleteConversation(id);
      if (id === conversationId) startNewChat();
      await refreshHistory();
    } catch (err) {
      setError(errorText(err));
    }
  }

  async function ask(question: string) {
    const text = question.trim();
    if (!text || pending || !policyId) return;
    const id = nextId.current++;
    setTurns((prev) => [...prev, { id, question: text }]);
    setDraft('');
    setPending(true);
    try {
      const response = await api.ask(text, policyId, conversationId);
      const answer: Answer = {
        text: response.answer,
        status: response.status,
        citations: response.citations,
        callKit: response.callKit,
        latencyMs: response.latencyMs,
      };
      setTurns((prev) => prev.map((turn) => (turn.id === id ? { ...turn, answer } : turn)));
      setConversationId(response.conversationId);
      setActiveTurnId(id);
      setActiveCitation(null);
      void refreshHistory();
    } catch (err) {
      setTurns((prev) => prev.map((turn) => (turn.id === id ? { ...turn, error: errorText(err) } : turn)));
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
  const noPolicies = policies.loaded && policies.ready.length === 0;

  return (
    <div className="ask">
      <aside className="history" data-open={historyOpen || undefined} aria-label="Conversation history">
        <div className="history-head">
          <button type="button" className="new-chat" onClick={() => startNewChat()}>
            New question
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
        {history.length === 0 ? (
          <p className="small muted history-empty">Your past questions appear here.</p>
        ) : (
          <ul className="history-list">
            {history.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  className="history-item"
                  aria-current={item.id === conversationId ? 'true' : undefined}
                  onClick={() => void openConversation(item.id)}
                  title={`${item.title}\n${policyName(item.policyId)}`}
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
        <div className="policy-picker">
          <label>
            <span className="small muted">Asking about</span>
            <select
              value={policyId ?? ''}
              disabled={conversationId !== null || policies.ready.length === 0}
              onChange={(e) => startNewChat(e.target.value)}
            >
              {policies.ready.length === 0 && <option value="">No policy yet</option>}
              {policies.ready.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.fileName}
                </option>
              ))}
            </select>
          </label>
          {conversationId && <span className="small muted">Start a new question to switch policy.</span>}
        </div>
        {error && <p className="error">{error}</p>}

        {turns.length === 0 && (
          <div className="intro">
            <h1>Ask your policy</h1>
            <p>
              Ask in English, French or Chinese. Answers come only from your policy and cite the page. If the policy
              is silent, you get the insurer's number, your policy numbers and a script for the call.
            </p>
            {noPolicies ? (
              <p className="notice">Add a policy under My documents first.</p>
            ) : (
              <>
                <p className="intro-label">Try one of these</p>
                <ul className="examples">
                  {EXAMPLES.map((example) => (
                    <li key={example}>
                      <button type="button" onClick={() => void ask(example)} disabled={!policyId}>
                        {example}
                      </button>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>
        )}

        {turns.map((turn) => (
          <article key={turn.id} className="turn" aria-current={turn.id === activeTurnId ? 'true' : undefined}>
            <p className="question">{turn.question}</p>

            {!turn.answer && !turn.error && <p className="pending">Reading your policy…</p>}
            {turn.error && (
              <p className="error" role="alert">
                The question could not be answered: {turn.error}
              </p>
            )}

            {turn.answer && (
              <div className="answer">
                {turn.answer.status === 'UNCLEAR' && (
                  <p className="status-banner" data-status="UNCLEAR">
                    The policy is not clear on this. Read the clauses in Sources and confirm with your insurer.
                  </p>
                )}
                {turn.answer.text.split(/\n+/).map((paragraph, p) => (
                  <p key={p}>
                    {paragraph.split(MARKER).map((part, i) => {
                      const match = part.match(/^\[(\d{1,2})\]$/);
                      if (!match) return part;
                      const index = Number(match[1]);
                      if (!turn.answer!.citations.some((c) => c.index === index)) return null;
                      return (
                        <button
                          key={i}
                          type="button"
                          className="marker"
                          aria-pressed={turn.id === activeTurnId && activeCitation === index}
                          onClick={() => focusCitation(turn.id, index)}
                        >
                          {index}
                        </button>
                      );
                    })}
                  </p>
                ))}
                {turn.answer.callKit && <CallKitCard kit={turn.answer.callKit} />}
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
          placeholder={conversationId ? 'Ask a follow-up…' : 'Ask about your policy…'}
          aria-label="Ask about your policy"
          rows={2}
          maxLength={2000}
          disabled={!policyId}
        />
        <button type="submit" className="primary" disabled={pending || !draft.trim() || !policyId}>
          Ask
        </button>
      </form>
    </div>
  );
}

/** What to have ready and what to say when calling the insurer. */
function CallKitCard({ kit }: { kit: CallKit }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(kit.script);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // clipboard unavailable: the script is still visible to select by hand
    }
  }

  return (
    <div className="call-kit">
      <h3>Call {kit.insurerName ?? 'your insurer'}</h3>
      <div className="call-kit-top">
        {kit.phone ? (
          <a className="phone" href={`tel:${kit.phone.value.replace(/[^\d+]/g, '')}`}>
            {kit.phone.value}
          </a>
        ) : (
          <span className="muted">No phone number found in the policy.</span>
        )}
        {kit.hours && <span className="small">{kit.hours.value}</span>}
      </div>
      {kit.details.length > 0 && (
        <dl className="facts">
          {kit.details.map((d) => (
            <div key={d.label} className="fact" data-unverified={!d.verified || undefined}>
              <dt>{d.label}</dt>
              <dd>
                {d.value} <PageRef page={d.page} />
              </dd>
            </div>
          ))}
        </dl>
      )}
      <div className="script-head">
        <span className="small">What to say</span>
        <button type="button" className="quiet" onClick={() => void copy()}>
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>
      <p className="script">{kit.script}</p>
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
  if (!turn?.answer) return <p className="muted">The policy clauses behind the selected answer appear here.</p>;
  if (turn.answer.citations.length === 0) return <p className="muted">Your policy has no clause on this.</p>;

  return (
    <ol className="source-list">
      {turn.answer.citations.map((citation) => (
        <li key={citation.index} id={`source-${citation.index}`}>
          <button
            type="button"
            className="source"
            aria-pressed={activeCitation === citation.index}
            onClick={() => onSelect(citation.index)}
          >
            <span className="source-index">{citation.index}</span>
            <span className="source-body">
              <span className="source-section">
                {citation.page != null ? `Page ${citation.page}` : citation.section ?? citation.fileName}
                {citation.page != null && citation.section && ` · ${citation.section}`}
              </span>
              <span className="source-file">{citation.fileName}</span>
              <span className="source-snippet">{citation.snippet}</span>
            </span>
          </button>
        </li>
      ))}
    </ol>
  );
}
