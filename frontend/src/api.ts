export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED';
export type DocumentKind = 'POLICY' | 'RECEIPT' | 'FORM';
export type AnswerStatus = 'ANSWERED' | 'UNCLEAR' | 'NOT_IN_POLICY';
export type ClaimType = 'SECONDARY_PARAMEDICAL' | 'SECONDARY_DENTAL' | 'SECONDARY_DRUGS' | 'SECONDARY_VISION';
export type Relationship = 'SELF' | 'SPOUSE' | 'CHILD';
export type SourceType =
  | 'POLICY'
  | 'OTHER_POLICY'
  | 'RECEIPT'
  | 'PROFILE'
  | 'CLAIM_SETUP'
  | 'CALCULATED'
  | 'USER'
  | 'MISSING';

export interface User {
  id: number;
  username: string;
  displayName: string;
}

export interface Fact {
  key: string;
  value: string;
  quote: string | null;
  page: number | null;
  verified: boolean;
}

export interface UploadedDocument {
  id: string;
  kind: DocumentKind;
  fileName: string;
  sizeBytes: number;
  status: DocumentStatus;
  chunkCount: number | null;
  errorMessage: string | null;
  createdAt: string;
  processedAt: string | null;
  facts: Fact[];
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

export interface CallKitDetail {
  label: string;
  value: string;
  page: number | null;
  verified: boolean;
}

export interface CallKit {
  insurerName: string | null;
  phone: CallKitDetail | null;
  hours: CallKitDetail | null;
  details: CallKitDetail[];
  script: string;
}

export interface ChatAnswer {
  answer: string;
  status: AnswerStatus;
  language: 'en' | 'fr' | 'zh';
  citations: Citation[];
  callKit: CallKit | null;
  latencyMs: number;
  conversationId: string;
  policyId: string;
}

export interface ConversationSummary {
  id: string;
  policyId: string;
  title: string;
  updatedAt: string;
}

export interface ChatMessage {
  sender: 'MEMBER' | 'ASSISTANT';
  content: string;
  status: AnswerStatus | null;
  citations: Citation[];
  callKit: CallKit | null;
  createdAt: string;
}

export interface Conversation {
  id: string;
  policyId: string;
  title: string;
  updatedAt: string;
  messages: ChatMessage[];
}

export interface GuideItem {
  text: string;
  page: number | null;
  section: string | null;
  clause: string;
}

export interface ClaimGuide {
  policyId: string;
  claimType: ClaimType;
  claimTypeLabel: string;
  deadlines: GuideItem[];
  documents: GuideItem[];
  submission: GuideItem[];
  coverage: GuideItem[];
  preApproval: { required: 'YES' | 'NO' | 'UNKNOWN'; basis: GuideItem | null };
  found: boolean;
}

export interface DraftField {
  key: string;
  label: string;
  value: string | null;
  sourceType: SourceType;
  sourceLabel: string | null;
  page: number | null;
  quote: string | null;
  verified: boolean;
  reviewed: boolean;
}

export interface ClaimDraft {
  id: string;
  claimType: ClaimType;
  claimTypeLabel: string;
  policy: { id: string; fileName: string } | null;
  otherPolicy: { id: string; fileName: string } | null;
  receipt: { id: string; fileName: string } | null;
  form: { key: string; name: string };
  relationship: Relationship;
  fields: DraftField[];
  leftForYou: string[];
  readyToDownload: boolean;
  createdAt: string;
  updatedAt: string;
}

/** A claim form: built into the app, or a fillable PDF the user uploaded. */
export interface FormOption {
  key: string;
  name: string;
  builtIn: boolean;
  status: DocumentStatus;
  fieldCount: number | null;
  errorMessage: string | null;
  createdAt: string | null;
}

export interface AppNotification {
  type: 'DOCUMENT_READY' | 'DOCUMENT_FAILED';
  documentId: string;
  kind: DocumentKind;
  fileName: string;
  message: string;
  at: string;
}

export interface ActivityEntry {
  action: string;
  targetType: string | null;
  targetId: string | null;
  detail: string | null;
  at: string;
}

export interface Profile {
  fullName: string | null;
  dateOfBirth: string | null;
  street: string | null;
  city: string | null;
  province: string | null;
  postalCode: string | null;
  phone: string | null;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: User;
}

/** Readable names for the facts read from documents. */
export const FACT_LABELS: Record<string, string> = {
  INSURER_NAME: 'Insurer',
  INSURER_PHONE: 'Phone',
  INSURER_HOURS: 'Hours',
  POLICY_NUMBER: 'Group policy number',
  CERTIFICATE_NUMBER: 'Certificate number',
  PLAN_MEMBER_NAME: 'Plan member',
  PLAN_SPONSOR: 'Plan sponsor',
  CLAIMS_ADDRESS: 'Claims address',
  PROVIDER_NAME: 'Provider',
  PATIENT_NAME: 'Patient',
  SERVICE_DATE: 'Date of service',
  SERVICE_TYPE: 'Service',
  AMOUNT_CHARGED: 'Amount charged',
  AMOUNT_PAID_BY_OTHER_PLAN: 'Paid by another plan',
  RECEIPT_NUMBER: 'Receipt number',
};

// ---------- Session token ----------

const TOKEN_KEY = 'claimpilot.token';
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

/** The live notification stream. EventSource cannot send headers, so the token goes in the URL. */
export function notificationStreamUrl() {
  return token ? `/api/notifications/stream?access_token=${encodeURIComponent(token)}` : null;
}

export function hasToken() {
  return token !== null;
}

/** Called when the server rejects the token, for example after it expires. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

// ---------- HTTP ----------

async function send(url: string, init: RequestInit = {}): Promise<Response> {
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
    if (response.status === 401 && !url.startsWith('/api/auth/')) onUnauthorized();
    throw new Error(detail);
  }
  return response;
}

async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const response = await send(url, init);
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

function json(method: string, body: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

function uploadTo(collection: 'policies' | 'receipts' | 'forms', file: File) {
  const form = new FormData();
  form.append('file', file);
  return request<UploadedDocument>(`/api/${collection}`, { method: 'POST', body: form });
}

export const api = {
  login: (username: string, password: string) =>
    request<LoginResponse>('/api/auth/login', json('POST', { username, password })),
  register: (username: string, displayName: string, password: string) =>
    request<LoginResponse>('/api/auth/register', json('POST', { username, displayName, password })),
  me: () => request<User>('/api/auth/me'),

  policies: () => request<UploadedDocument[]>('/api/policies'),
  uploadPolicy: (file: File) => uploadTo('policies', file),
  deletePolicy: (id: string) => request<void>(`/api/policies/${id}`, { method: 'DELETE' }),
  receipts: () => request<UploadedDocument[]>('/api/receipts'),
  receipt: (id: string) => request<UploadedDocument>(`/api/receipts/${id}`),
  uploadReceipt: (file: File) => uploadTo('receipts', file),
  deleteReceipt: (id: string) => request<void>(`/api/receipts/${id}`, { method: 'DELETE' }),

  ask: (question: string, policyId: string, conversationId: string | null) =>
    request<ChatAnswer>('/api/chat', json('POST', { question, policyId, conversationId })),
  conversations: () => request<ConversationSummary[]>('/api/conversations'),
  conversation: (id: string) => request<Conversation>(`/api/conversations/${id}`),
  deleteConversation: (id: string) => request<void>(`/api/conversations/${id}`, { method: 'DELETE' }),

  claimTypes: () => request<{ type: ClaimType; label: string }[]>('/api/claims/types'),
  guide: (policyId: string, type: ClaimType) =>
    request<ClaimGuide>(`/api/claims/guide?policyId=${policyId}&type=${type}`),
  drafts: () => request<ClaimDraft[]>('/api/claims'),
  draft: (id: string) => request<ClaimDraft>(`/api/claims/${id}`),
  createDraft: (body: {
    claimType: ClaimType;
    policyId: string;
    otherPolicyId: string | null;
    receiptId: string | null;
    relationship: Relationship;
    formKey: string | null;
  }) => request<ClaimDraft>('/api/claims', json('POST', body)),
  forms: () => request<FormOption[]>('/api/forms'),
  uploadForm: (file: File) => uploadTo('forms', file),
  deleteForm: (id: string) => request<void>(`/api/forms/${id}`, { method: 'DELETE' }),
  updateField: (id: string, key: string, change: { value?: string; reviewed?: boolean }) =>
    request<ClaimDraft>(`/api/claims/${id}/fields/${key}`, json('PATCH', change)),
  deleteDraft: (id: string) => request<void>(`/api/claims/${id}`, { method: 'DELETE' }),
  /** Downloads the filled PDF through the browser. */
  downloadPdf: async (id: string) => {
    const response = await send(`/api/claims/${id}/pdf`);
    const disposition = response.headers.get('Content-Disposition') ?? '';
    const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? 'claim.pdf';
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    link.click();
    URL.revokeObjectURL(url);
  },

  notifications: () => request<AppNotification[]>('/api/notifications'),
  activity: () => request<ActivityEntry[]>('/api/audit'),

  profile: () => request<Profile>('/api/profile'),
  saveProfile: (profile: Profile) => request<Profile>('/api/profile', json('PUT', profile)),
  deleteAccount: () => request<void>('/api/account', { method: 'DELETE' }),
};
