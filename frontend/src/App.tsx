import { useState } from 'react';
import { useSession } from './auth';
import AskView from './components/AskView';
import ClaimView from './components/ClaimView';
import DocumentsView from './components/DocumentsView';
import LoginView from './components/LoginView';
import ProfileView from './components/ProfileView';

type View = 'ask' | 'claim' | 'documents' | 'profile';

const TABS: { view: View; label: string }[] = [
  { view: 'ask', label: 'Ask' },
  { view: 'claim', label: 'Claim' },
  { view: 'documents', label: 'My documents' },
  { view: 'profile', label: 'Profile' },
];

export default function App() {
  const { user, restoring, signOut } = useSession();
  const [view, setView] = useState<View>('ask');

  if (restoring) return null;
  if (!user) return <LoginView />;

  return (
    <div className="shell">
      <header className="topbar">
        <span className="wordmark">ClaimPilot</span>
        <nav className="tabs">
          {TABS.map((tab) => (
            <button
              key={tab.view}
              type="button"
              className="tab"
              aria-current={view === tab.view ? 'page' : undefined}
              onClick={() => setView(tab.view)}
            >
              {tab.label}
            </button>
          ))}
        </nav>
        <div className="account">
          <span className="account-name">{user.displayName}</span>
          <button type="button" className="quiet" onClick={signOut}>
            Sign out
          </button>
        </div>
      </header>

      <main className="main">
        {view === 'ask' && <AskView key={user.id} />}
        {view === 'claim' && <ClaimView key={user.id} />}
        {view === 'documents' && <DocumentsView />}
        {view === 'profile' && <ProfileView />}
      </main>
      <footer className="disclaimer">
        ClaimPilot explains what your policy says. It is not insurance or legal advice. Demo data is fictional.
      </footer>
    </div>
  );
}
