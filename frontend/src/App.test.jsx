import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.jsx';

const invoice = {
  id: '11111111-1111-1111-1111-111111111111',
  title: 'March invoice',
  filename: 'invoice.pdf',
  contentType: 'application/pdf',
  sizeBytes: 2048,
  uploadedAt: '2026-03-01T10:00:00Z',
};

function json(body, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

/** A tiny fake of the backend: routes fetch calls to handlers keyed by "METHOD url". */
function mockApi(handlers) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((url, options = {}) => {
    const key = `${options.method ?? 'GET'} ${url}`;
    const handler = handlers[key];
    if (!handler) throw new Error(`Unexpected request: ${key}`);
    return handler(options);
  });
}

describe('App', () => {
  beforeEach(() => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  it('lists documents with download links', async () => {
    mockApi({ 'GET /api/documents': () => json([invoice]) });

    render(<App />);

    const row = (await screen.findByText('March invoice')).closest('tr');
    expect(within(row).getByText('invoice.pdf')).toBeInTheDocument();
    expect(within(row).getByText('2.0 KB')).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: 'Download' })).toHaveAttribute(
      'href',
      `/api/documents/${invoice.id}/content`,
    );
  });

  it('shows a hint when there are no documents', async () => {
    mockApi({ 'GET /api/documents': () => json([]) });

    render(<App />);

    expect(await screen.findByText('No documents yet. Upload one above.')).toBeInTheDocument();
  });

  it('uploads a file with a title and reloads the list', async () => {
    const user = userEvent.setup();
    let uploaded = [];
    let sentForm;
    mockApi({
      'GET /api/documents': () => json(uploaded),
      'POST /api/documents': (options) => {
        sentForm = options.body;
        uploaded = [invoice];
        return json(invoice, 201);
      },
    });
    render(<App />);
    await screen.findByText('No documents yet. Upload one above.');

    const file = new File(['%PDF'], 'invoice.pdf', { type: 'application/pdf' });
    await user.upload(screen.getByLabelText('File'), file);
    await user.type(screen.getByLabelText(/Title/), 'March invoice');
    await user.click(screen.getByRole('button', { name: 'Upload' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Uploaded “March invoice”.');
    expect(screen.getByRole('cell', { name: 'March invoice' })).toBeInTheDocument();
    expect(sentForm.get('file').name).toBe('invoice.pdf');
    expect(sentForm.get('title')).toBe('March invoice');
  });

  it('shows the server error when an upload fails', async () => {
    const user = userEvent.setup();
    mockApi({
      'GET /api/documents': () => json([]),
      'POST /api/documents': () => json({ detail: 'The uploaded file is empty.' }, 400),
    });
    render(<App />);

    await user.upload(screen.getByLabelText('File'), new File([''], 'empty.txt'));
    await user.click(screen.getByRole('button', { name: 'Upload' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The uploaded file is empty.');
  });

  it('searches as the user types', async () => {
    const user = userEvent.setup();
    const fetchMock = mockApi({
      'GET /api/documents': () => json([invoice]),
      'GET /api/documents?q=tax': () => json([]),
    });
    render(<App />);
    await screen.findByText('March invoice');

    await user.type(screen.getByRole('searchbox', { name: 'Search documents' }), 'tax');

    expect(await screen.findByText('No documents match “tax”.')).toBeInTheDocument();
    // debounced: no request for "t" or "ta"
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/documents', '/api/documents?q=tax']);
  });

  it('renames a document', async () => {
    const user = userEvent.setup();
    let current = invoice;
    let sentBody;
    mockApi({
      'GET /api/documents': () => json([current]),
      [`PATCH /api/documents/${invoice.id}`]: (options) => {
        sentBody = JSON.parse(options.body);
        current = { ...invoice, title: sentBody.title };
        return json(current);
      },
    });
    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'Rename March invoice' }));
    const input = screen.getByRole('textbox', { name: 'New title for March invoice' });
    await user.clear(input);
    await user.type(input, 'April invoice');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Renamed to “April invoice”.');
    expect(sentBody).toEqual({ title: 'April invoice' });
    expect(screen.getByRole('cell', { name: 'April invoice' })).toBeInTheDocument();
  });

  it('deletes a document after confirmation', async () => {
    const user = userEvent.setup();
    let documents = [invoice];
    mockApi({
      'GET /api/documents': () => json(documents),
      [`DELETE /api/documents/${invoice.id}`]: () => {
        documents = [];
        return Promise.resolve(new Response(null, { status: 204 }));
      },
    });
    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'Delete March invoice' }));

    expect(window.confirm).toHaveBeenCalled();
    expect(await screen.findByRole('status')).toHaveTextContent('Deleted “March invoice”.');
    await waitFor(() => expect(screen.queryByText('invoice.pdf')).not.toBeInTheDocument());
  });

  it('keeps the document when deletion is cancelled', async () => {
    const user = userEvent.setup();
    window.confirm.mockReturnValue(false);
    const fetchMock = mockApi({ 'GET /api/documents': () => json([invoice]) });
    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'Delete March invoice' }));

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(screen.getByText('invoice.pdf')).toBeInTheDocument();
  });

  it('shows the text extraction status of each document', async () => {
    mockApi({
      'GET /api/documents': () =>
        json([
          { ...invoice, id: 'a', title: 'Scan', textStatus: 'PENDING' },
          { ...invoice, id: 'b', title: 'Photo', textStatus: 'FAILED' },
          { ...invoice, id: 'c', title: 'Notes', textStatus: 'NOT_EXTRACTED' },
        ]),
    });

    render(<App />);

    expect(within((await screen.findByText('Scan')).closest('tr')).getByText('Extracting…')).toBeInTheDocument();
    expect(within(screen.getByText('Photo').closest('tr')).getByText('Failed')).toBeInTheDocument();
    expect(within(screen.getByText('Notes').closest('tr')).getByText('—')).toBeInTheDocument();
  });

  it('shows search snippets with the matched words highlighted', async () => {
    const user = userEvent.setup();
    mockApi({
      'GET /api/documents': () => json([invoice]),
      'GET /api/documents?q=betrag': () =>
        json([{ ...invoice, textStatus: 'COMPLETED', snippet: 'Rechnung 4711 Gesamtbetrag 120 EUR' }]),
    });
    render(<App />);
    await screen.findByText('March invoice');

    await user.type(screen.getByRole('searchbox', { name: 'Search documents' }), 'betrag');

    const mark = await screen.findByText('betrag', { selector: 'mark' });
    expect(mark.closest('p')).toHaveTextContent('Rechnung 4711 Gesamtbetrag 120 EUR');
  });

  it('shows the extracted text on demand', async () => {
    const user = userEvent.setup();
    mockApi({
      'GET /api/documents': () => json([{ ...invoice, textStatus: 'COMPLETED' }]),
      [`GET /api/documents/${invoice.id}/text`]: () => Promise.resolve(new Response('Rechnung Nr. 4711')),
    });
    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'View text of March invoice' }));

    expect(await screen.findByLabelText('Extracted text of March invoice')).toHaveTextContent('Rechnung Nr. 4711');
    await user.click(screen.getByRole('button', { name: 'Hide text of March invoice' }));
    expect(screen.queryByLabelText('Extracted text of March invoice')).not.toBeInTheDocument();
  });

  it('refreshes the list until text extraction finishes', async () => {
    let status = 'PENDING';
    mockApi({
      'GET /api/documents': () => {
        const response = json([{ ...invoice, textStatus: status }]);
        status = 'COMPLETED';
        return response;
      },
    });

    render(<App />);

    expect(await screen.findByText('Extracting…')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'View text of March invoice' }, { timeout: 4000 })).toBeInTheDocument();
  });

  it('reports when the backend is unreachable', async () => {
    mockApi({ 'GET /api/documents': () => Promise.resolve(new Response('Bad gateway', { status: 502 })) });

    render(<App />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Request failed (HTTP 502).');
  });
});
