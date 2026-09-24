import { useState, type FormEvent } from 'react';
import { useSession } from '../auth';

// Seeded by the backend's V3 migration for the fictional company Northbridge Software.
const DEMO_ACCOUNTS = [
  { username: 'ivan', who: 'Ivan Morel', role: 'Employee, IT' },
  { username: 'fiona', who: 'Fiona Tremblay', role: 'Employee, Finance' },
  { username: 'hana', who: 'Hana Park', role: 'Knowledge manager, HR' },
  { username: 'admin', who: 'Avery Admin', role: 'Admin' },
];
const DEMO_PASSWORD = 'demo1234';

export default function LoginView() {
  const { signIn } = useSession();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await signIn(username.trim(), password);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login">
      <div className="login-card">
        <span className="wordmark">CompanyBrain</span>
        <h1>Sign in</h1>
        <form onSubmit={submit} className="form">
          <label>
            Username
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              autoFocus
              required
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </label>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="primary" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <div className="demo-accounts">
          <p className="small muted">
            Demo accounts, password <code>{DEMO_PASSWORD}</code>. Each department sees different documents.
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
                  <span className="small muted">{account.role}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </main>
  );
}
