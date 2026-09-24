import { useState, type FormEvent } from 'react';
import { api, type AssistantReply, type AssistantStep } from '../api';
import { CallKitCard } from './AskView';
import { GuideView } from './ClaimView';
import { errorText, PageRef } from './shared';

const EXAMPLES = [
  'My physio cost $120 and my own plan paid $84. Can I claim the rest on my husband\'s plan?',
  'Is massage therapy covered on Marc\'s plan, and do I need a referral?',
  'How do I claim new glasses?',
];

const ACTION_LABELS = { ASK: 'Answer', GUIDE: 'Claim rules', FILL: 'Claim form' } as const;

type Choice = { policyId?: string; claimType?: string; relationship?: string };

interface Exchange {
  id: number;
  message: string;
  /** Answers already given to the assistant's questions for this message. */
  choice: Choice;
  reply: AssistantReply | null;
  error: string | null;
}

/**
 * Phase 4: describe the situation once. The assistant plans which modules to use (answer, claim
 * rules, pre-filled form), asks back when something is ambiguous, and shows each result.
 */
export default function AssistantView({ onOpenClaim }: { onOpenClaim: (draftId: string) => void }) {
  const [exchanges, setExchanges] = useState<Exchange[]>([]);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  async function send(text: string, choice: Choice = {}) {
    const trimmed = text.trim();
    if (!trimmed || busy) return;
    const id = Date.now();
    setExchanges((list) => [...list, { id, message: trimmed, choice, reply: null, error: null }]);
    setMessage('');
    setBusy(true);
    try {
      const reply = await api.assistant(trimmed, choice);
      setExchanges((list) => list.map((e) => (e.id === id ? { ...e, reply } : e)));
    } catch (err) {
      setExchanges((list) => list.map((e) => (e.id === id ? { ...e, error: errorText(err) } : e)));
    } finally {
      setBusy(false);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void send(message);
  }

  return (
    <div className="page assistant">
      <div className="page-head">
        <h1>Assistant</h1>
        <p className="muted">
          Describe your situation in one message, in any language. The assistant decides whether to answer from your
          policy, show what a claim needs, or pre-fill the claim form, and does them in order. Each result keeps its
          sources; you still check and sign the form yourself.
        </p>
      </div>

      {exchanges.length === 0 && (
        <div className="examples">
          {EXAMPLES.map((example) => (
            <button key={example} type="button" className="example" onClick={() => void send(example)}>
              {example}
            </button>
          ))}
        </div>
      )}

      <ol className="exchanges">
        {exchanges.map((exchange) => (
          <li key={exchange.id} className="exchange">
            <p className="exchange-message">{exchange.message}</p>
            {!exchange.reply && !exchange.error && <p className="pending">Working out the steps…</p>}
            {exchange.error && <p className="error">{exchange.error}</p>}
            {exchange.reply && (
              <ReplyView
                reply={exchange.reply}
                onChoose={(field, value) => void send(exchange.message, { ...exchange.choice, [field]: value })}
                onOpenClaim={onOpenClaim}
              />
            )}
          </li>
        ))}
      </ol>

      <form className="assistant-form" onSubmit={submit}>
        <textarea
          value={message}
          rows={2}
          placeholder="For example: my dentist bill was $240, my plan paid $150, claim the rest on my wife's plan"
          onChange={(e) => setMessage(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              void send(message);
            }
          }}
        />
        <button type="submit" className="primary" disabled={busy || !message.trim()}>
          {busy ? 'Working…' : 'Send'}
        </button>
      </form>
    </div>
  );
}

function ReplyView({
  reply,
  onChoose,
  onOpenClaim,
}: {
  reply: AssistantReply;
  onChoose: (field: 'policyId' | 'claimType' | 'relationship', value: string) => void;
  onOpenClaim: (draftId: string) => void;
}) {
  return (
    <div className="assistant-reply">
      {reply.actions.length > 0 && (
        <ol className="plan" aria-label="Plan">
          {reply.actions.map((action) => (
            <li key={action}>{ACTION_LABELS[action]}</li>
          ))}
        </ol>
      )}
      {reply.steps[0]?.type !== 'CLARIFY' && <p className="summary">{reply.summary}</p>}
      {reply.steps.map((step, index) => (
        <StepView key={index} step={step} onChoose={onChoose} onOpenClaim={onOpenClaim} />
      ))}
      <p className="latency">Done in {(reply.latencyMs / 1000).toFixed(1)} s</p>
    </div>
  );
}

function StepView({
  step,
  onChoose,
  onOpenClaim,
}: {
  step: AssistantStep;
  onChoose: (field: 'policyId' | 'claimType' | 'relationship', value: string) => void;
  onOpenClaim: (draftId: string) => void;
}) {
  if (step.type === 'CLARIFY' && step.clarify) {
    const clarify = step.clarify;
    return (
      <section className="step-card">
        <h3>{clarify.question}</h3>
        <div className="choices">
          {clarify.options.map((option) => (
            <button
              key={option.value}
              type="button"
              className="secondary"
              onClick={() => clarify.field && onChoose(clarify.field, option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </section>
    );
  }
  if (step.type === 'ANSWER' && step.answer) {
    const answer = step.answer;
    return (
      <section className="step-card">
        <h3>{step.title}</h3>
        <p className="status-line" data-status={answer.status}>
          {answer.status === 'ANSWERED' ? 'Answered' : answer.status === 'UNCLEAR' ? 'Unclear' : 'Not in the policy'}
        </p>
        <p className="answer-text">{answer.answer}</p>
        {answer.citations.length > 0 && (
          <ol className="mini-sources">
            {answer.citations.map((c) => (
              <li key={c.index}>
                <span className="source-index">{c.index}</span> <PageRef page={c.page} /> {c.snippet}
              </li>
            ))}
          </ol>
        )}
        {answer.callKit && <CallKitCard kit={answer.callKit} />}
      </section>
    );
  }
  if (step.type === 'GUIDE' && step.guide) {
    return (
      <section className="step-card">
        <h3>{step.title}</h3>
        <GuideView guide={step.guide} />
      </section>
    );
  }
  if (step.type === 'CLAIM' && step.draft) {
    const draft = step.draft;
    const missing = draft.fields.filter((f) => f.sourceType === 'MISSING');
    return (
      <section className="step-card">
        <h3>{step.title}</h3>
        <p>
          {draft.claimTypeLabel}. {draft.fields.length - missing.length} of {draft.fields.length} fields filled from
          your documents and profile
          {missing.length > 0 && `; still empty: ${missing.map((f) => f.label).join(', ')}`}.
        </p>
        <button type="button" className="primary" onClick={() => onOpenClaim(draft.id)}>
          Check the form and download it
        </button>
      </section>
    );
  }
  return (
    <section className="step-card" data-type={step.type}>
      <h3>{step.title}</h3>
      <p>{step.text}</p>
    </section>
  );
}
