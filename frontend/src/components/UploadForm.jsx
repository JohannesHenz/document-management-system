import { useRef, useState } from 'react';
import { uploadDocument } from '../api.js';

export default function UploadForm({ onUploaded }) {
  const [file, setFile] = useState(null);
  const [title, setTitle] = useState('');
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const fileInput = useRef(null);

  async function handleSubmit(event) {
    event.preventDefault();
    if (!file) return;
    setUploading(true);
    setError('');
    try {
      const document = await uploadDocument(file, title);
      setFile(null);
      setTitle('');
      fileInput.current.value = '';
      onUploaded(document);
    } catch (e) {
      setError(e.message);
    } finally {
      setUploading(false);
    }
  }

  return (
    <section className="card" aria-labelledby="upload-heading">
      <h2 id="upload-heading">Upload a document</h2>
      <form className="upload-form" onSubmit={handleSubmit}>
        <label>
          File
          <input ref={fileInput} type="file" onChange={(event) => setFile(event.target.files[0] ?? null)} />
        </label>
        <label>
          Title <span className="hint">(optional, defaults to the file name)</span>
          <input type="text" maxLength={255} value={title} onChange={(event) => setTitle(event.target.value)} />
        </label>
        <button type="submit" className="primary" disabled={!file || uploading}>
          {uploading ? 'Uploading…' : 'Upload'}
        </button>
      </form>
      {error && (
        <p className="message error" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
