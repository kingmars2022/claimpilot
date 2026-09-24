import { useCallback, useEffect, useRef, useState, type DragEvent } from 'react';
import { api, type KnowledgeDocument } from '../api';

const ACCEPT = '.pdf,.docx,.md,.txt';
const POLL_MS = 2000;
const LOCALE = 'en-CA';

const STATUS_LABELS: Record<KnowledgeDocument['status'], string> = {
  UPLOADED: 'Waiting',
  PROCESSING: 'Indexing',
  INDEXED: 'Ready',
  FAILED: 'Failed',
};

const dateFormat = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short' });

/** Human-readable file size, for example "4.1 kB". */
function formatSize(bytes: number) {
  const units = ['byte', 'kilobyte', 'megabyte'] as const;
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return new Intl.NumberFormat(LOCALE, {
    style: 'unit',
    unit: units[unit],
    unitDisplay: 'short',
    maximumFractionDigits: unit === 0 ? 0 : 1,
  }).format(value);
}

export default function LibraryView() {
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [uploading, setUploading] = useState<string | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const refresh = useCallback(async () => {
    try {
      setDocuments(await api.listDocuments());
    } catch (err) {
      setErrors([err instanceof Error ? err.message : String(err)]);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // Poll only while something is still being indexed.
  const busy = documents.some((doc) => doc.status === 'UPLOADED' || doc.status === 'PROCESSING');
  useEffect(() => {
    if (!busy) return;
    const timer = setInterval(() => void refresh(), POLL_MS);
    return () => clearInterval(timer);
  }, [busy, refresh]);

  async function upload(files: FileList | File[]) {
    const problems: string[] = [];
    for (const file of Array.from(files)) {
      setUploading(file.name);
      try {
        await api.uploadDocument(file);
      } catch (err) {
        problems.push(`${file.name} was not added: ${err instanceof Error ? err.message : String(err)}`);
      }
    }
    setUploading(null);
    setErrors(problems);
    await refresh();
  }

  async function remove(doc: KnowledgeDocument) {
    if (!window.confirm(`Delete ${doc.fileName}? It will no longer be used to answer questions.`)) return;
    try {
      await api.deleteDocument(doc.id);
    } catch (err) {
      setErrors([err instanceof Error ? err.message : String(err)]);
    }
    await refresh();
  }

  function onDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    setDragging(false);
    if (event.dataTransfer.files.length > 0) void upload(event.dataTransfer.files);
  }

  return (
    <div className="library">
      <div className="library-head">
        <h1>Library</h1>
        <p className="muted">Documents added here become searchable in a few seconds.</p>
      </div>

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
          ref={inputRef}
          type="file"
          accept={ACCEPT}
          multiple
          className="visually-hidden"
          onChange={(event) => {
            if (event.target.files?.length) void upload(event.target.files);
            event.target.value = '';
          }}
        />
        <span>
          Drop files here, or <span className="link">choose files</span>
        </span>
        <span className="muted small">{uploading ? `Adding ${uploading}…` : 'PDF, Word, Markdown or plain text, up to 20 MB each'}</span>
      </label>

      {errors.map((message) => (
        <p key={message} className="error" role="alert">
          {message}
        </p>
      ))}

      {documents.length === 0 ? (
        <p className="empty">No documents yet. Add a handbook or policy to start asking questions.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Status</th>
                <th scope="col" className="num">Sections</th>
                <th scope="col" className="num">Size</th>
                <th scope="col">Added</th>
                <th scope="col"><span className="visually-hidden">Delete</span></th>
              </tr>
            </thead>
            <tbody>
              {documents.map((doc) => (
                <tr key={doc.id}>
                  <td className="file">{doc.fileName}</td>
                  <td>
                    <span className="status" data-status={doc.status} title={doc.errorMessage ?? undefined}>
                      {STATUS_LABELS[doc.status]}
                    </span>
                    {doc.status === 'FAILED' && doc.errorMessage && <span className="status-detail">{doc.errorMessage}</span>}
                  </td>
                  <td className="num">{doc.chunkCount ?? ''}</td>
                  <td className="num">{formatSize(doc.sizeBytes)}</td>
                  <td className="date">{dateFormat.format(new Date(doc.createdAt))}</td>
                  <td className="actions">
                    <button type="button" className="quiet" onClick={() => void remove(doc)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
