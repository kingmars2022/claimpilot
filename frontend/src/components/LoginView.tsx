import { useState, type FormEvent } from 'react';
import { useSession } from '../auth';
import { errorText } from './shared';

// Seeded by the backend's V2 migration. Fictional people.
const DEMO_ACCOUNTS = [
  { username: 'fiona', who: 'Fiona Tremblay', note: 'Profile filled in' },
  { username: 'sam', who: 'Sam Okafor', note: 'Empty account' },
];
const DEMO_PASSWORD = 'demo1234';

export default function LoginView() {
  const { signIn, register } = useSession();
  const [mode, setMode] = useState<'signin' | 'register'>('signin');
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'signin') await signIn(username.trim(), password);
      else await register(username.trim(), displayName.trim(), password);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login">
      <div className="login-card">
        <span className="wordmark">ClaimPilot</span>
        <p className="login-pitch">
          Ask your insurance policy in plain language, see what a claim needs, and get the claim form filled in from
          your own documents.
        </p>
        <h1>{mode === 'signin' ? 'Sign in' : 'Create an account'}</h1>
        <form onSubmit={submit} className="form">
          <label>
            Username
            <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required />
          </label>
          {mode === 'register' && (
            <label>
              Your name
              <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} autoComplete="name" required />
            </label>
          )}
          <label>
            Password
            <input
              type="password"
              value={password}
              minLength={mode === 'register' ? 8 : undefined}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
              required
            />
          </label>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="primary" disabled={busy}>
            {busy ? 'Please wait…' : mode === 'signin' ? 'Sign in' : 'Create account'}
          </button>
        </form>
        <button
          type="button"
          className="link"
          onClick={() => {
            setMode(mode === 'signin' ? 'register' : 'signin');
            setError(null);
          }}
        >
          {mode === 'signin' ? 'New here? Create an account' : 'I already have an account'}
        </button>

        {mode === 'signin' && (
          <div className="demo-accounts">
            <p className="small muted">
              Demo accounts, password <code>{DEMO_PASSWORD}</code>. Sample documents are in <code>sample-docs/</code>.
            </p>
            <ul>
              {DEMO_ACCOUNTS.map((account) => (
                <li key={account.username}>
                  <button
                    type="button"
                    onClick={() => {
                      setUsername(account.username);
                      setPassword(DEMO_PASSWORD);
                    }}
                  >
                    <span className="demo-name">{account.who}</span>
                    <span className="small muted">{account.note}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
        <p className="small muted">
          ClaimPilot explains what your policy says. It is not insurance or legal advice, and it never signs or
          submits anything for you.
        </p>
      </div>
    </main>
  );
}
