import { useCallback, useEffect, useState } from 'react';
import {
  api,
  type ClaimDraft,
  type ClaimGuide,
  type ClaimType,
  type DraftField,
  type GuideItem,
  type Relationship,
  type SourceType,
} from '../api';
import { dateTime, Dropzone, errorText, PageRef, uploadAll, useDocuments, useForms } from './shared';

const RELATIONSHIPS: { value: Relationship; label: string }[] = [
  { value: 'SPOUSE', label: "I'm the plan member's spouse" },
  { value: 'SELF', label: "I'm the plan member" },
  { value: 'CHILD', label: 'Child of the plan member' },
];

const SOURCE_LABELS: Record<SourceType, string> = {
  POLICY: 'Policy',
  OTHER_POLICY: 'Other policy',
  RECEIPT: 'Receipt',
  PROFILE: 'Profile',
  CLAIM_SETUP: 'Your choice',
  CALCULATED: 'Calculated',
  USER: 'Entered by you',
  MISSING: 'Missing',
};

export default function ClaimView() {
  const [drafts, setDrafts] = useState<ClaimDraft[]>([]);
  const [open, setOpen] = useState<ClaimDraft | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setDrafts(await api.drafts());
    } catch (err) {
      setError(errorText(err));
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return (
    <div className="page">
      {error && <p className="error">{error}</p>}
      {open ? (
        <DraftReview
          draft={open}
          onChange={setOpen}
          onClose={() => {
            setOpen(null);
            void refresh();
          }}
        />
      ) : (
        <>
          <div className="page-head">
            <h1>Make a claim</h1>
            <p className="muted">
              For the part of a bill your own plan did not pay: see what your spouse's plan requires, then get its
              claim form filled in from your policies, the receipt and your profile. You check every field, sign
              and submit it yourself.
            </p>
          </div>
          {drafts.length > 0 && (
            <section className="doc-section">
              <h2>Your claim forms</h2>
              <ul className="draft-list">
                {drafts.map((d) => (
                  <li key={d.id}>
                    <button type="button" className="draft-item" onClick={() => setOpen(d)}>
                      <span>{d.claimTypeLabel}</span>
                      <span className="small muted">
                        {d.receipt?.fileName ?? 'No receipt'} · {dateTime.format(new Date(d.updatedAt))} ·{' '}
                        {d.readyToDownload ? 'Ready to download' : `${d.fields.filter((f) => f.reviewed).length} of ${d.fields.length} checked`}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          )}
          <NewClaim
            onCreated={(draft) => {
              setOpen(draft);
              void refresh();
            }}
          />
        </>
      )}
    </div>
  );
}

function NewClaim({ onCreated }: { onCreated: (draft: ClaimDraft) => void }) {
  const policies = useDocuments('POLICY');
  const receipts = useDocuments('RECEIPT');
  const { forms, refresh: refreshForms } = useForms();
  const [formKey, setFormKey] = useState('builtin:cedarview-secondary');
  const [types, setTypes] = useState<{ type: ClaimType; label: string }[]>([]);
  const [claimType, setClaimType] = useState<ClaimType>('SECONDARY_PARAMEDICAL');
  const [policyId, setPolicyId] = useState('');
  const [otherPolicyId, setOtherPolicyId] = useState('');
  const [receiptId, setReceiptId] = useState('');
  const [relationship, setRelationship] = useState<Relationship>('SPOUSE');
  const [guide, setGuide] = useState<ClaimGuide | null>(null);
  const [guideLoading, setGuideLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.claimTypes().then(setTypes, (err) => setError(errorText(err)));
  }, []);

  useEffect(() => {
    if (!receiptId && receipts.ready.length > 0) setReceiptId(receipts.ready[0].id);
  }, [receiptId, receipts.ready]);

  useEffect(() => {
    setGuide(null);
    if (!policyId) return;
    let cancelled = false;
    setGuideLoading(true);
    api
      .guide(policyId, claimType)
      .then((g) => !cancelled && setGuide(g), (err) => !cancelled && setError(errorText(err)))
      .finally(() => !cancelled && setGuideLoading(false));
    return () => {
      cancelled = true;
    };
  }, [policyId, claimType]);

  async function create() {
    setBusy(true);
    setError(null);
    try {
      onCreated(
        await api.createDraft({
          claimType,
          policyId,
          otherPolicyId: otherPolicyId || null,
          receiptId: receiptId || null,
          relationship,
          formKey,
        }),
      );
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  const readyPolicies = policies.ready;
  if (policies.loaded && readyPolicies.length === 0) {
    return <p className="notice">Add the policy you want to claim on under My documents first.</p>;
  }

  return (
    <div className="claim-steps">
      <section className="step">
        <h2>
          <span className="step-number">1</span> The claim
        </h2>
        <div className="form-grid">
          <label>
            Type of care
            <select value={claimType} onChange={(e) => setClaimType(e.target.value as ClaimType)}>
              {types.map((t) => (
                <option key={t.type} value={t.type}>
                  {t.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Claim on (the plan that pays second)
            <select value={policyId} onChange={(e) => setPolicyId(e.target.value)}>
              <option value="">Choose a policy</option>
              {readyPolicies.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.fileName}
                </option>
              ))}
            </select>
          </label>
          <label>
            Plan that paid first (optional)
            <select value={otherPolicyId} onChange={(e) => setOtherPolicyId(e.target.value)}>
              <option value="">None</option>
              {readyPolicies
                .filter((p) => p.id !== policyId)
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.fileName}
                  </option>
                ))}
            </select>
            {policyId && readyPolicies.length < 2 && (
              <span className="field-hint">Add the plan that paid first under My documents to choose it here.</span>
            )}
          </label>
          <label>
            Who received the care
            <select value={relationship} onChange={(e) => setRelationship(e.target.value as Relationship)}>
              {RELATIONSHIPS.map((r) => (
                <option key={r.value} value={r.value}>
                  {r.label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </section>

      <section className="step">
        <h2>
          <span className="step-number">2</span> What this policy requires
        </h2>
        {!policyId && <p className="muted">Choose the policy to see its deadlines and required documents.</p>}
        {guideLoading && <p className="pending">Reading the claim rules in your policy…</p>}
        {guide && <GuideView guide={guide} />}
      </section>

      <section className="step">
        <h2>
          <span className="step-number">3</span> The receipt
        </h2>
        {receipts.ready.length > 0 && (
          <label className="inline-label">
            Receipt
            <select value={receiptId} onChange={(e) => setReceiptId(e.target.value)}>
              <option value="">No receipt</option>
              {receipts.ready.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.fileName}
                </option>
              ))}
            </select>
          </label>
        )}
        <Dropzone
          accept=".pdf,.png,.jpg,.jpeg"
          hint="PDF or photo; the amounts and date are read automatically"
          onFiles={async (files) => {
            const problems = await uploadAll(files, async (file) => {
              const uploaded = await api.uploadReceipt(file);
              setReceiptId(uploaded.id);
            });
            if (problems.length) setError(problems.join(' '));
            await receipts.refresh();
          }}
        >
          Add a receipt: drop it here, or <span className="link">choose a file</span>
        </Dropzone>
        {receipts.documents.some((r) => r.status === 'PROCESSING' || r.status === 'UPLOADED') && (
          <p className="pending">Reading the receipt…</p>
        )}
      </section>

      <section className="step">
        <h2>
          <span className="step-number">4</span> The claim form
        </h2>
        <p className="muted small">
          Choose the form to fill: a built-in one, or your insurer's own fillable PDF. Each field is matched by its
          label once per form version. Signature, declaration and bank details are always left for you.
        </p>
        <label className="inline-label">
          Claim form
          <select value={formKey} onChange={(e) => setFormKey(e.target.value)}>
            {forms
              .filter((f) => f.status === 'READY')
              .map((f) => (
                <option key={f.key} value={f.key}>
                  {f.name}
                  {f.fieldCount ? ` (${f.fieldCount} fields)` : ''}
                </option>
              ))}
          </select>
        </label>
        {forms
          .filter((f) => !f.builtIn && f.status !== 'READY')
          .map((f) => (
            <p key={f.key} className={f.status === 'FAILED' ? 'error' : 'pending'}>
              {f.name}: {f.status === 'FAILED' ? f.errorMessage : 'reading its fields…'}
            </p>
          ))}
        <Dropzone
          accept=".pdf"
          hint="A fillable (interactive) PDF from your insurer's website"
          onFiles={async (files) => {
            const problems = await uploadAll(files, async (file) => {
              const uploaded = await api.uploadForm(file);
              setFormKey(`upload:${uploaded.id}`);
            });
            if (problems.length) setError(problems.join(' '));
            await refreshForms();
          }}
        >
          Use your insurer's form: drop it here, or <span className="link">choose a file</span>
        </Dropzone>
        {policyId && (!otherPolicyId || !receiptId) && (
          <p className="field-hint">
            {!receiptId && 'No receipt chosen: the provider, date and amounts will be left empty. '}
            {!otherPolicyId && 'No plan that paid first: its insurer and numbers will be left empty.'}
          </p>
        )}
        {error && <p className="error">{error}</p>}
        <button type="button" className="primary" disabled={!policyId || busy} onClick={() => void create()}>
          {busy ? 'Filling in the form…' : 'Fill in the claim form'}
        </button>
      </section>
    </div>
  );
}

function GuideView({ guide }: { guide: ClaimGuide }) {
  const [done, setDone] = useState<Set<number>>(new Set());
  if (!guide.found) {
    return <p className="notice">This policy does not describe how to claim for this type of care. Call your insurer.</p>;
  }
  const pre = guide.preApproval;
  return (
    <div className="guide">
      {guide.deadlines.length > 0 && (
        <div className="guide-block deadline">
          <h3>Deadlines</h3>
          <ul>
            {guide.deadlines.map((item, i) => (
              <li key={i}>
                <GuideText item={item} />
              </li>
            ))}
          </ul>
        </div>
      )}
      {pre.basis && (
        <div className="guide-block" data-required={pre.required}>
          <h3>Before the care</h3>
          <p>
            <strong>{pre.required === 'YES' ? 'Approval or referral needed. ' : pre.required === 'NO' ? 'No approval needed. ' : ''}</strong>
            <GuideText item={pre.basis} />
          </p>
        </div>
      )}
      {guide.documents.length > 0 && (
        <div className="guide-block">
          <h3>Documents to include</h3>
          <ul className="checklist">
            {guide.documents.map((item, i) => (
              <li key={i}>
                <label>
                  <input
                    type="checkbox"
                    checked={done.has(i)}
                    onChange={(e) => {
                      const next = new Set(done);
                      if (e.target.checked) next.add(i);
                      else next.delete(i);
                      setDone(next);
                    }}
                  />
                  <GuideText item={item} />
                </label>
              </li>
            ))}
          </ul>
        </div>
      )}
      {guide.submission.length > 0 && (
        <div className="guide-block">
          <h3>How to submit</h3>
          <ul>
            {guide.submission.map((item, i) => (
              <li key={i}>
                <GuideText item={item} />
              </li>
            ))}
          </ul>
        </div>
      )}
      {guide.coverage.length > 0 && (
        <div className="guide-block">
          <h3>What the plan pays</h3>
          <ul>
            {guide.coverage.map((item, i) => (
              <li key={i}>
                <GuideText item={item} />
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function GuideText({ item }: { item: GuideItem }) {
  return (
    <span className="guide-text" title={item.clause}>
      {item.text} <PageRef page={item.page} />
    </span>
  );
}

function DraftReview({
  draft,
  onChange,
  onClose,
}: {
  draft: ClaimDraft;
  onChange: (draft: ClaimDraft) => void;
  onClose: () => void;
}) {
  const [error, setError] = useState<string | null>(null);
  const reviewed = draft.fields.filter((f) => f.reviewed).length;

  async function update(field: DraftField, change: { value?: string; reviewed?: boolean }) {
    try {
      onChange(await api.updateField(draft.id, field.key, change));
      setError(null);
    } catch (err) {
      setError(errorText(err));
    }
  }

  async function download() {
    try {
      await api.downloadPdf(draft.id);
    } catch (err) {
      setError(errorText(err));
    }
  }

  async function remove() {
    if (!window.confirm('Delete this claim form?')) return;
    try {
      await api.deleteDraft(draft.id);
      onClose();
    } catch (err) {
      setError(errorText(err));
    }
  }

  return (
    <div className="draft">
      <button type="button" className="link" onClick={onClose}>
        ← All claims
      </button>
      <div className="page-head">
        <h1>Check your claim form</h1>
        <p className="muted">
          {draft.claimTypeLabel}. Claim on {draft.policy?.fileName ?? 'a deleted policy'}
          {draft.otherPolicy && `, first paid by ${draft.otherPolicy.fileName}`}
          {draft.receipt && `, receipt ${draft.receipt.fileName}`}. Form: {draft.form.name}.
        </p>
      </div>

      <div className="review-bar">
        <div className="progress" aria-label={`${reviewed} of ${draft.fields.length} fields checked`}>
          <div className="progress-fill" style={{ width: `${(reviewed / Math.max(draft.fields.length, 1)) * 100}%` }} />
        </div>
        <span className="small">
          {reviewed} of {draft.fields.length} fields checked
        </span>
        <button type="button" className="primary" disabled={!draft.readyToDownload} onClick={() => void download()}>
          Download filled PDF
        </button>
      </div>
      {!draft.readyToDownload && (
        <p className="field-hint">
          Tick each field once you have checked it; the PDF can be downloaded when all are ticked. An empty field can be
          filled in here, or ticked and completed on the PDF.
        </p>
      )}
      {error && <p className="error">{error}</p>}

      <div className="table-wrap">
        <table className="review-table">
          <thead>
            <tr>
              <th scope="col">Field</th>
              <th scope="col">Value</th>
              <th scope="col">From</th>
              <th scope="col">Checked</th>
            </tr>
          </thead>
          <tbody>
            {draft.fields.map((field) => (
              <FieldRow key={field.key} field={field} onUpdate={(change) => void update(field, change)} />
            ))}
          </tbody>
        </table>
      </div>

      <div className="left-for-you">
        <h2>Left for you to complete</h2>
        <p className="muted small">ClaimPilot never fills these in. Complete them on the PDF, sign it and submit it yourself.</p>
        <ul>
          {draft.leftForYou.map((label) => (
            <li key={label}>{label}</li>
          ))}
        </ul>
      </div>

      <button type="button" className="quiet" onClick={() => void remove()}>
        Delete this claim form
      </button>
    </div>
  );
}

function FieldRow({ field, onUpdate }: { field: DraftField; onUpdate: (change: { value?: string; reviewed?: boolean }) => void }) {
  const [value, setValue] = useState(field.value ?? '');

  useEffect(() => {
    setValue(field.value ?? '');
  }, [field.value]);

  return (
    <tr data-missing={field.sourceType === 'MISSING' || undefined} data-reviewed={field.reviewed || undefined}>
      <th scope="row">{field.label}</th>
      <td>
        <input
          aria-label={field.label}
          value={value}
          placeholder={field.sourceType === 'MISSING' ? 'Not found: fill in' : ''}
          onChange={(e) => setValue(e.target.value)}
          onBlur={() => {
            if (value !== (field.value ?? '')) onUpdate({ value });
          }}
        />
        {!field.verified && field.sourceType !== 'MISSING' && (
          <span className="check-badge" title="Could not be confirmed in the document text. Check it carefully.">
            check
          </span>
        )}
      </td>
      <td className="source-cell">
        <span className="source-chip" data-source={field.sourceType}>
          {SOURCE_LABELS[field.sourceType]}
        </span>
        {field.sourceLabel && <span className="small muted"> {field.sourceLabel}</span>}
        {field.quote && <span className="quote">“{field.quote}”</span>}
      </td>
      <td>
        <input
          type="checkbox"
          aria-label={`Checked: ${field.label}`}
          checked={field.reviewed}
          onChange={(e) => onUpdate({ reviewed: e.target.checked })}
        />
      </td>
    </tr>
  );
}
