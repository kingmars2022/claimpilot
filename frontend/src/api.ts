export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'INDEXED' | 'FAILED';

export interface KnowledgeDocument {
  id: string;
  fileName: string;
  sizeBytes: number;
  status: DocumentStatus;
  chunkCount: number | null;
  errorMessage: string | null;
  createdAt: string;
  indexedAt: string | null;
}

export interface Citation {
  index: number;
  documentId: string;
  fileName: string;
  page: number | null;
  section: string | null;
  snippet: string;
  score: number | null;
}

export interface ChatAnswer {
  answer: string;
  grounded: boolean;
  citations: Citation[];
  latencyMs: number;
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    try {
      const problem = await response.json();
      if (problem?.detail) detail = problem.detail;
    } catch {
      // body was not JSON
    }
    throw new Error(detail);
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

export const api = {
  ask: (question: string) =>
    request<ChatAnswer>('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question }),
    }),

  listDocuments: () => request<KnowledgeDocument[]>('/api/documents'),

  uploadDocument: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<KnowledgeDocument>('/api/documents', { method: 'POST', body: form });
  },

  deleteDocument: (id: string) => request<void>(`/api/documents/${id}`, { method: 'DELETE' }),
};
