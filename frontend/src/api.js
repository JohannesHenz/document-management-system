const BASE = '/api/documents';

async function request(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  return response.status === 204 ? null : response.json();
}

async function errorMessage(response) {
  if (response.status === 413) {
    return 'The file is too large (maximum 20 MB).';
  }
  try {
    const problem = await response.json();
    if (problem.detail) return problem.detail;
  } catch {
    // not a JSON problem response
  }
  return `Request failed (HTTP ${response.status}).`;
}

export function listDocuments(query = '') {
  const trimmed = query.trim();
  return request(trimmed ? `${BASE}?q=${encodeURIComponent(trimmed)}` : BASE);
}

export function uploadDocument(file, title = '') {
  const body = new FormData();
  body.append('file', file);
  if (title.trim()) body.append('title', title.trim());
  return request(BASE, { method: 'POST', body });
}

export function renameDocument(id, title) {
  return request(`${BASE}/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  });
}

export function deleteDocument(id) {
  return request(`${BASE}/${id}`, { method: 'DELETE' });
}

export function contentUrl(id) {
  return `${BASE}/${id}/content`;
}
