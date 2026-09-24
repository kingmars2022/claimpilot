import { useState } from 'react';
import { api, type UploadedDocument } from '../api';
import { dateTime, Dropzone, errorText, FactList, STATUS_LABELS, uploadAll, useDocuments } from './shared';

/** Policies and receipts: upload, see what was read from each, delete. */
export default function DocumentsView() {
  const policies = useDocuments('POLICY');
  const receipts = useDocuments('RECEIPT');
  const [errors, setErrors] = useState<string[]>([]);

  async function remove(doc: UploadedDocument) {
    if (!window.confirm(`Delete ${doc.fileName}? Its text and everything read from it are removed.`)) return;
    try {
      await (doc.kind === 'POLICY' ? api.deletePolicy(doc.id) : api.deleteReceipt(doc.id));
    } catch (err) {
      setErrors([errorText(err)]);
    }
    await (doc.kind === 'POLICY' ? policies.refresh() : receipts.refresh());
  }

  return (
    <div className="page">
      <div className="page-head">
        <h1>My documents</h1>
        <p className="muted">
          Add your benefits booklets and receipts. Scanned PDFs and photos are read with text recognition. Each key
          detail shows the page it came from; anything marked <span className="check-badge">check</span> could not be
          confirmed in the document and should be checked.
        </p>
      </div>

      {errors.map((message) => (
        <p key={message} className="error" role="alert">
          {message}
        </p>
      ))}

      <section className="doc-section">
        <h2>Policies</h2>
        <Dropzone
          accept=".pdf,.png,.jpg,.jpeg,.docx,.txt,.md"
          hint="PDF (digital or scanned), photo, or Word, up to 20 MB"
          onFiles={async (files) => {
            setErrors(await uploadAll(files, api.uploadPolicy));
            await policies.refresh();
          }}
        />
        <DocumentList documents={policies.documents} loaded={policies.loaded} onDelete={remove}
                      empty="No policy yet. Add your group benefits booklet, and your spouse's if you have two plans." />
      </section>

      <section className="doc-section">
        <h2>Receipts</h2>
        <Dropzone
          accept=".pdf,.png,.jpg,.jpeg"
          hint="PDF or photo of the receipt"
          onFiles={async (files) => {
            setErrors(await uploadAll(files, api.uploadReceipt));
            await receipts.refresh();
          }}
        />
        <DocumentList documents={receipts.documents} loaded={receipts.loaded} onDelete={remove}
                      empty="No receipt yet." />
      </section>
    </div>
  );
}

function DocumentList({
  documents,
  loaded,
  onDelete,
  empty,
}: {
  documents: UploadedDocument[];
  loaded: boolean;
  onDelete: (doc: UploadedDocument) => void;
  empty: string;
}) {
  if (!loaded) return null;
  if (documents.length === 0) return <p className="empty">{empty}</p>;
  return (
    <ul className="doc-list">
      {documents.map((doc) => (
        <li key={doc.id} className="doc-card">
          <div className="doc-card-head">
            <span className="file">{doc.fileName}</span>
            <span className="status" data-status={doc.status}>
              {STATUS_LABELS[doc.status]}
            </span>
            <span className="muted small">{dateTime.format(new Date(doc.createdAt))}</span>
            <button type="button" className="quiet" onClick={() => onDelete(doc)}>
              Delete
            </button>
          </div>
          {doc.status === 'FAILED' && <p className="error small">{doc.errorMessage}</p>}
          {doc.status === 'READY' && <FactList facts={doc.facts} />}
          {(doc.status === 'UPLOADED' || doc.status === 'PROCESSING') && (
            <p className="muted small">Reading the document and its key details…</p>
          )}
        </li>
      ))}
    </ul>
  );
}
