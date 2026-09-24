export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'INDEXED' | 'FAILED';
export type Role = 'EMPLOYEE' | 'KNOWLEDGE_MANAGER' | 'ADMIN';

export interface Department {
  id: number;
  code: string;
  name: string;
}

export interface User {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  department: Department | null;
  createdAt: string;
}

export interface KnowledgeDocument {
  id: string;
  fileName: string;
  sizeBytes: number;
  status: DocumentStatus;
  chunkCount: number | null;
  errorMessage: string | null;
  createdAt: string;
  indexedAt: string | null;
  /** Empty means the whole company. */
  visibleTo: Department[];
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
  conversationId: string;
}

export interface ConversationSummary {
  id: string;
  title: string;
  updatedAt: string;
}

export interface ChatMessage {
  sender: 'EMPLOYEE' | 'ASSISTANT';
  content: string;
  grounded: boolean | null;
  citations: Citation[];
  createdAt: string;
}

export interface Conversation {
  id: string;
  title: string;
  updatedAt: string;
  messages: ChatMessage[];
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface UserInput {
  username?: string;
  displayName: string;
  password?: string;
  role: Role;
  departmentId: number | null;
}

export const ROLE_LABELS: Record<Role, string> = {
  EMPLOYEE: 'Employee',
  KNOWLEDGE_MANAGER: 'Knowledge manager',
  ADMIN: 'Admin',
};

// ---------- Session token ----------

const TOKEN_KEY = 'companybrain.token';
let token: string | null = readStoredToken();
let onUnauthorized: () => void = () => {};

function readStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(value: string | null) {
  token = value;
  try {
    if (value) localStorage.setItem(TOKEN_KEY, value);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // storage unavailable: the session lasts until the page is reloaded
  }
}

export function hasToken() {
  return token !== null;
}

/** Called when the server rejects the token, for example after it expires. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

// ---------- HTTP ----------

async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(url, { ...init, headers });
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    try {
      const problem = await response.json();
      if (problem?.detail) detail = problem.detail;
    } catch {
      // body was not JSON
    }
    if (response.status === 401 && url !== '/api/auth/login') onUnauthorized();
    throw new Error(detail);
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

function json(method: string, body: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

export const api = {
  login: (username: string, password: string) =>
    request<LoginResponse>('/api/auth/login', json('POST', { username, password })),
  me: () => request<User>('/api/auth/me'),
  departments: () => request<Department[]>('/api/departments'),

  ask: (question: string, conversationId: string | null) =>
    request<ChatAnswer>('/api/chat', json('POST', { question, conversationId })),
  conversations: () => request<ConversationSummary[]>('/api/conversations'),
  conversation: (id: string) => request<Conversation>(`/api/conversations/${id}`),
  deleteConversation: (id: string) => request<void>(`/api/conversations/${id}`, { method: 'DELETE' }),

  listDocuments: () => request<KnowledgeDocument[]>('/api/documents'),
  uploadDocument: (file: File, departmentIds: number[]) => {
    const form = new FormData();
    form.append('file', file);
    departmentIds.forEach((id) => form.append('departmentIds', String(id)));
    return request<KnowledgeDocument>('/api/documents', { method: 'POST', body: form });
  },
  setVisibility: (id: string, departmentIds: number[]) =>
    request<KnowledgeDocument>(`/api/documents/${id}/visibility`, json('PUT', { departmentIds })),
  deleteDocument: (id: string) => request<void>(`/api/documents/${id}`, { method: 'DELETE' }),

  users: () => request<User[]>('/api/admin/users'),
  createUser: (input: UserInput) => request<User>('/api/admin/users', json('POST', input)),
  updateUser: (id: number, input: UserInput) => request<User>(`/api/admin/users/${id}`, json('PUT', input)),
  deleteUser: (id: number) => request<void>(`/api/admin/users/${id}`, { method: 'DELETE' }),
  createDepartment: (code: string, name: string) =>
    request<Department>('/api/admin/departments', json('POST', { code, name })),
};
