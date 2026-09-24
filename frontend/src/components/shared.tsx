import { useCallback, useEffect, useRef, useState, type DragEvent, type ReactNode } from 'react';
import { api, FACT_LABELS, type DocumentKind, type Fact, type FormOption, type UploadedDocument } from '../api';

const POLL_MS = 2000;
const LOCALE = 'en-CA';

export const dateTime = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short' });

export function errorText(err: unknown) {
  return err instanceof Error ? err.message : String(err);
}

export const STATUS_LABELS: Record<UploadedDocument['status'], string> = {
  UPLOADED: 'Waiting',
  PROCESSING: 'Reading',
  READY: 'Ready',
  FAILED: 'Failed',
};

/** Name of the window event fired when the server says a document finished processing. */
export const DOCUMENT_EVENT = 'claimpilot:document';

/** Calls refresh as soon as a notification says a document is ready (polling stays as a fallback). */
function useDocumentEvents(refresh: () => Promise<void>) {
  useEffect(() => {
    const listener = () => void refresh();
    window.addEventListener(DOCUMENT_EVENT, listener);
    return () => window.removeEventListener(DOCUMENT_EVENT, listener);
  }, [refresh]);
}

/** The user's policies or receipts, refreshed every 2 s while any is still being read. */
export function useDocuments(kind: DocumentKind) {
  const [documents, setDocuments] = useState<UploadedDocument[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setDocuments(await (kind === 'POLICY' ? api.policies() : api.receipts()));
      setError(null);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setLoaded(true);
    }
  }, [kind]);

  useEffect(() => {
    void refresh();
  }, [refresh]);
  useDocumentEvents(refresh);

  const busy = documents.some((d) => d.status === 'UPLOADED' || d.status === 'PROCESSING');
  useEffect(() => {
    if (!busy) return;
    const timer = setInterval(() => void refresh(), POLL_MS);
    return () => clearInterval(timer);
  }, [busy, refresh]);

  return { documents, ready: documents.filter((d) => d.status === 'READY'), loaded, error, refresh };
}

/** Claim forms: the built-in ones and the user's uploads, refreshed while an upload is being read. */
export function useForms() {
  const [forms, setForms] = useState<FormOption[]>([]);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setForms(await api.forms());
      setError(null);
    } catch (err) {
      setError(errorText(err));
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);
  useDocumentEvents(refresh);

  const busy = forms.some((f) => f.status === 'UPLOADED' || f.status === 'PROCESSING');
  useEffect(() => {
    if (!busy) return;
    const timer = setInterval(() => void refresh(), POLL_MS);
    return () => clearInterval(timer);
  }, [busy, refresh]);

  return { forms, error, refresh };
}

interface DropzoneProps {
  accept: string;
  hint: string;
  onFiles: (files: File[]) => Promise<void>;
  children?: ReactNode;
}

/** Drop files here or choose them. Shows which file is being sent. */
export function Dropzone({ accept, hint, onFiles, children }: DropzoneProps) {
  const [dragging, setDragging] = useState(false);
  const [sending, setSending] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  async function send(files: File[]) {
    if (files.length === 0) return;
    setSending(true);
    try {
      await onFiles(files);
    } finally {
      setSending(false);
    }
  }

  function onDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    setDragging(false);
    void send(Array.from(event.dataTransfer.files));
  }

  return (
    <label
      className="dropzone"
      data-dragging={dragging || undefined}
      onDragOver={(event) => {
        event.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={onDrop}
    >
      <input
        ref={input}
        type="file"
        accept={accept}
        multiple
        className="visually-hidden"
        onChange={(event) => {
          void send(Array.from(event.target.files ?? []));
          event.target.value = '';
        }}
      />
      <span>{children ?? <>Drop files here, or <span className="link">choose files</span></>}</span>
      <span className="muted small">{sending ? 'Uploading…' : hint}</span>
    </label>
  );
}

/** Uploads files one by one and returns an error message per failed file. */
export async function uploadAll(files: File[], upload: (file: File) => Promise<unknown>): Promise<string[]> {
  const problems: string[] = [];
  for (const file of files) {
    try {
      await upload(file);
    } catch (err) {
      problems.push(`${file.name} was not added: ${errorText(err)}`);
    }
  }
  return problems;
}

/** "page 2" chip; nothing when the page is unknown. */
export function PageRef({ page }: { page: number | null | undefined }) {
  if (page == null) return null;
  return <span className="page-ref">p. {page}</span>;
}

/** The key facts read from a document, each with its page and whether it was confirmed. */
export function FactList({ facts }: { facts: Fact[] }) {
  if (facts.length === 0) return <p className="muted small">No key details found yet.</p>;
  return (
    <dl className="facts">
      {facts.map((fact) => (
        <div key={fact.key} className="fact" data-unverified={!fact.verified || undefined}>
          <dt>{FACT_LABELS[fact.key] ?? fact.key}</dt>
          <dd>
            <span title={fact.quote ?? undefined}>{fact.value}</span> <PageRef page={fact.page} />
            {!fact.verified && (
              <span className="check-badge" title="This value could not be found in the document text. Check it.">
                check
              </span>
            )}
          </dd>
        </div>
      ))}
    </dl>
  );
}
