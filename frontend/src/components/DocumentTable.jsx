import { Fragment, useState } from 'react';
import { contentUrl, deleteDocument, fetchText, renameDocument } from '../api.js';
import { formatBytes, formatDate } from '../format.js';
import Highlight from './Highlight.jsx';

const COLUMNS = 6;

export default function DocumentTable({ documents, query = '', emptyText, onRenamed, onDeleted, onError }) {
  if (documents.length === 0) {
    return <p className="empty">{emptyText}</p>;
  }
  return (
    <div className="table-wrapper">
      <table>
        <thead>
          <tr>
            <th scope="col">Title</th>
            <th scope="col">File</th>
            <th scope="col" className="numeric">
              Size
            </th>
            <th scope="col">Uploaded</th>
            <th scope="col">Text</th>
            <th scope="col">
              <span className="visually-hidden">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {documents.map((doc) => (
            <DocumentRow
              key={doc.id}
              doc={doc}
              query={query}
              onRenamed={onRenamed}
              onDeleted={onDeleted}
              onError={onError}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function DocumentRow({ doc, query, onRenamed, onDeleted, onError }) {
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(doc.title);
  const [busy, setBusy] = useState(false);
  const [text, setText] = useState(null);

  async function run(action, onSuccess) {
    setBusy(true);
    try {
      onSuccess(await action());
    } catch (e) {
      onError(e.message);
    } finally {
      setBusy(false);
    }
  }

  function saveTitle(event) {
    event.preventDefault();
    run(
      () => renameDocument(doc.id, title.trim()),
      (updated) => {
        setEditing(false);
        onRenamed(updated);
      },
    );
  }

  function remove() {
    if (window.confirm(`Delete “${doc.title}”? This cannot be undone.`)) {
      run(
        () => deleteDocument(doc.id),
        () => onDeleted(doc),
      );
    }
  }

  function toggleText() {
    if (text !== null) {
      setText(null);
    } else {
      run(() => fetchText(doc.id), setText);
    }
  }

  return (
    <Fragment>
      <tr>
        <td>
          {editing ? (
            <form className="inline-form" onSubmit={saveTitle}>
              <input
                aria-label={`New title for ${doc.title}`}
                value={title}
                maxLength={255}
                required
                autoFocus
                onChange={(event) => setTitle(event.target.value)}
              />
              <button type="submit" disabled={busy || !title.trim()}>
                Save
              </button>
              <button
                type="button"
                onClick={() => {
                  setTitle(doc.title);
                  setEditing(false);
                }}
              >
                Cancel
              </button>
            </form>
          ) : (
            <>
              {doc.title}
              {doc.snippet && (
                <p className="snippet">
                  <Highlight text={doc.snippet} query={query} />
                </p>
              )}
            </>
          )}
        </td>
        <td className="filename">{doc.filename}</td>
        <td className="numeric">{formatBytes(doc.sizeBytes)}</td>
        <td className="nowrap">{formatDate(doc.uploadedAt)}</td>
        <td>
          <TextStatus doc={doc} open={text !== null} busy={busy} onToggle={toggleText} />
        </td>
        <td>
          <div className="actions">
            <a className="button" href={contentUrl(doc.id)} download={doc.filename}>
              Download
            </a>
            {!editing && (
              <button type="button" onClick={() => setEditing(true)} disabled={busy} aria-label={`Rename ${doc.title}`}>
                Rename
              </button>
            )}
            <button
              type="button"
              className="danger"
              onClick={remove}
              disabled={busy}
              aria-label={`Delete ${doc.title}`}
            >
              Delete
            </button>
          </div>
        </td>
      </tr>
      {text !== null && (
        <tr className="text-row">
          <td colSpan={COLUMNS}>
            <pre aria-label={`Extracted text of ${doc.title}`}>{text || '(no text found)'}</pre>
          </td>
        </tr>
      )}
    </Fragment>
  );
}

function TextStatus({ doc, open, busy, onToggle }) {
  switch (doc.textStatus) {
    case 'PENDING':
      return <span className="badge pending">Extracting…</span>;
    case 'COMPLETED':
      return (
        <button
          type="button"
          className="link"
          onClick={onToggle}
          disabled={busy}
          aria-expanded={open}
          aria-label={`${open ? 'Hide' : 'View'} text of ${doc.title}`}
        >
          {open ? 'Hide text' : 'View text'}
        </button>
      );
    case 'FAILED':
      return <span className="badge failed">Failed</span>;
    default:
      return (
        <span className="muted" title="No text extraction for this file type">
          —
        </span>
      );
  }
}
