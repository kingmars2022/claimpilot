import { useEffect, useState, type FormEvent } from 'react';
import { api, type ActivityEntry, type Custody, type Profile } from '../api';
import { useSession } from '../auth';
import { dateTime, errorText } from './shared';

const EMPTY: Profile = {
  fullName: null,
  dateOfBirth: null,
  street: null,
  city: null,
  province: null,
  postalCode: null,
  phone: null,
  spouseName: null,
  spouseDateOfBirth: null,
  custody: 'TOGETHER',
  otherParentName: null,
  otherParentDateOfBirth: null,
};

const CUSTODY: { value: Custody; label: string }[] = [
  { value: 'TOGETHER', label: 'With me and my spouse (their other parent)' },
  { value: 'SOLE_ME', label: 'Separated: I have custody' },
  { value: 'SOLE_OTHER_PARENT', label: 'Separated: their other parent has custody' },
  { value: 'JOINT', label: 'Separated: joint custody' },
];

type TextKey = Exclude<keyof Profile, 'custody'>;

const FIELDS: { key: TextKey; label: string; type?: string; autoComplete?: string }[] = [
  { key: 'fullName', label: 'Full name', autoComplete: 'name' },
  { key: 'dateOfBirth', label: 'Date of birth', type: 'date', autoComplete: 'bday' },
  { key: 'street', label: 'Street address', autoComplete: 'street-address' },
  { key: 'city', label: 'City', autoComplete: 'address-level2' },
  { key: 'province', label: 'Province', autoComplete: 'address-level1' },
  { key: 'postalCode', label: 'Postal code', autoComplete: 'postal-code' },
  { key: 'phone', label: 'Phone', type: 'tel', autoComplete: 'tel' },
  { key: 'spouseName', label: "Spouse's full name (as on their plan)" },
  { key: 'spouseDateOfBirth', label: "Spouse's date of birth", type: 'date' },
];

const OTHER_PARENT_FIELDS: { key: TextKey; label: string; type?: string }[] = [
  { key: 'otherParentName', label: "Children's other parent (as on their plan)" },
  { key: 'otherParentDateOfBirth', label: "Other parent's date of birth", type: 'date' },
];

const ACTION_LABELS: Record<string, string> = {
  SIGNED_IN: 'Signed in',
  SIGNED_UP: 'Account created',
  PROFILE_UPDATED: 'Profile updated',
  DOCUMENT_UPLOADED: 'Uploaded',
  DOCUMENT_READY: 'Read and ready',
  DOCUMENT_FAILED: 'Could not be read',
  DOCUMENT_DELETED: 'Deleted',
  CLAIM_CREATED: 'Claim form started',
  CLAIM_FIELD_CORRECTED: 'Claim field corrected',
  CLAIM_DOWNLOADED: 'Claim form downloaded',
  CLAIM_DELETED: 'Claim form deleted',
  ASSISTANT_USED: 'Assistant',
};

export default function ProfileView() {
  const { signOut } = useSession();
  const [profile, setProfile] = useState<Profile>(EMPTY);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [activity, setActivity] = useState<ActivityEntry[]>([]);

  useEffect(() => {
    api.profile().then(setProfile, (err) => setError(errorText(err)));
    api.activity().then(setActivity, () => setActivity([]));
  }, []);

  async function save(event: FormEvent) {
    event.preventDefault();
    try {
      setProfile(await api.saveProfile(profile));
      setMessage('Saved. New claim forms will use these details.');
      setError(null);
    } catch (err) {
      setError(errorText(err));
    }
  }

  async function deleteEverything() {
    const answer = window.prompt(
      'This deletes your account, policies, receipts, questions and claim forms. It cannot be undone. Type DELETE to confirm.',
    );
    if (answer !== 'DELETE') return;
    try {
      await api.deleteAccount();
      signOut();
    } catch (err) {
      setError(errorText(err));
    }
  }

  return (
    <div className="page narrow">
      <div className="page-head">
        <h1>My profile</h1>
        <p className="muted">
          Entered once, reused on every claim form. Your spouse's name and birthday tell ClaimPilot whose plan is whose
          and which pays first. ClaimPilot never asks for a social insurance number or bank details; forms that need
          them are left for you to complete.
        </p>
      </div>
      <form className="form profile-form" onSubmit={save}>
        {FIELDS.map((f) => (
          <label key={f.key}>
            {f.label}
            <input
              type={f.type ?? 'text'}
              autoComplete={f.autoComplete}
              value={profile[f.key] ?? ''}
              onChange={(e) => setProfile({ ...profile, [f.key]: e.target.value || null })}
            />
          </label>
        ))}
        <label>
          My children live
          <select
            value={profile.custody}
            onChange={(e) => setProfile({ ...profile, custody: e.target.value as Custody })}
          >
            {CUSTODY.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </select>
        </label>
        {profile.custody !== 'TOGETHER' && (
          <>
            <p className="muted small">
              For separated parents, custody decides which plan pays first for a child. Your spouse above is then the
              children's step-parent.
            </p>
            {OTHER_PARENT_FIELDS.map((f) => (
              <label key={f.key}>
                {f.label}
                <input
                  type={f.type ?? 'text'}
                  value={profile[f.key] ?? ''}
                  onChange={(e) => setProfile({ ...profile, [f.key]: e.target.value || null })}
                />
              </label>
            ))}
          </>
        )}
        {message && <p className="small">{message}</p>}
        {error && <p className="error">{error}</p>}
        <button type="submit" className="primary">
          Save profile
        </button>
      </form>

      <section className="activity">
        <h2>Activity</h2>
        <p className="muted small">What happened to your account and documents, newest first. Values read from your
          documents are never written here.</p>
        {activity.length === 0 ? (
          <p className="empty">Nothing yet.</p>
        ) : (
          <ul className="activity-list">
            {activity.slice(0, 30).map((entry, index) => (
              <li key={`${entry.at}-${index}`}>
                <span className="activity-action">{ACTION_LABELS[entry.action] ?? entry.action}</span>
                {entry.detail && <span className="activity-detail">{entry.detail}</span>}
                <span className="muted small">{dateTime.format(new Date(entry.at))}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="danger-zone">
        <h2>Delete my data</h2>
        <p className="muted small">
          Removes your account and everything linked to it: documents, the text read from them, questions, and claim
          forms.
        </p>
        <button type="button" className="danger" onClick={() => void deleteEverything()}>
          Delete my account and data
        </button>
      </section>
    </div>
  );
}
