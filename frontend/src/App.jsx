import { useEffect, useState } from 'react';
import { listDocuments } from './api.js';
import UploadForm from './components/UploadForm.jsx';
import SearchBar from './components/SearchBar.jsx';
import DocumentTable from './components/DocumentTable.jsx';

const SEARCH_DEBOUNCE_MS = 250;

export default function App() {
  const [documents, setDocuments] = useState([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let cancelled = false; // ignore responses to outdated searches
    const timer = setTimeout(
      async () => {
        try {
          const result = await listDocuments(query);
          if (!cancelled) {
            setDocuments(result);
            setError('');
          }
        } catch (e) {
          if (!cancelled) setError(e.message);
        } finally {
          if (!cancelled) setLoading(false);
        }
      },
      query ? SEARCH_DEBOUNCE_MS : 0,
    );
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [query, reloadToken]);

  function changed(message) {
    setNotice(message);
    setError('');
    setReloadToken((token) => token + 1);
  }

  return (
    <div className="page">
      <header>
        <h1>Document Management System</h1>
        <p className="subtitle">Upload, find and manage your documents.</p>
      </header>

      <main>
        <UploadForm onUploaded={(doc) => changed(`Uploaded “${doc.title}”.`)} />

        <section className="card" aria-labelledby="documents-heading">
          <div className="card-header">
            <h2 id="documents-heading">Documents</h2>
            <SearchBar value={query} onChange={setQuery} />
          </div>

          {error && (
            <p className="message error" role="alert">
              {error}
            </p>
          )}
          {notice && (
            <p className="message success" role="status">
              {notice}
            </p>
          )}

          {loading ? (
            <p className="empty">Loading…</p>
          ) : (
            <DocumentTable
              documents={documents}
              emptyText={query ? `No documents match “${query}”.` : 'No documents yet. Upload one above.'}
              onRenamed={(doc) => changed(`Renamed to “${doc.title}”.`)}
              onDeleted={(doc) => changed(`Deleted “${doc.title}”.`)}
              onError={setError}
            />
          )}
        </section>
      </main>
    </div>
  );
}
