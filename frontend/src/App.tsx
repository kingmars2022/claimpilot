import { useState } from 'react';
import { ROLE_LABELS } from './api';
import { useSession } from './auth';
import AdminView from './components/AdminView';
import AskView from './components/AskView';
import LibraryView from './components/LibraryView';
import LoginView from './components/LoginView';

type View = 'ask' | 'library' | 'admin';

export default function App() {
  const { user, restoring, signOut, can } = useSession();
  const [view, setView] = useState<View>('ask');

  if (restoring) return null;
  if (!user) return <LoginView />;

  const tabs: { view: View; label: string }[] = [
    { view: 'ask', label: 'Ask' },
    ...(can('KNOWLEDGE_MANAGER', 'ADMIN') ? [{ view: 'library' as const, label: 'Library' }] : []),
    ...(can('ADMIN') ? [{ view: 'admin' as const, label: 'Admin' }] : []),
  ];
  const current = tabs.some((t) => t.view === view) ? view : 'ask';

  return (
    <div className="shell">
      <header className="topbar">
        <span className="wordmark">CompanyBrain</span>

        <nav className="tabs">
          {tabs.map((tab) => (
            <button
              key={tab.view}
              type="button"
              className="tab"
              aria-current={current === tab.view ? 'page' : undefined}
              onClick={() => setView(tab.view)}
            >
              {tab.label}
            </button>
          ))}
        </nav>

        <div className="account">
          <span className="account-name">{user.displayName}</span>
          <span className="small muted">
            {ROLE_LABELS[user.role]}
            {user.department && ` · ${user.department.name}`}
          </span>
          <button type="button" className="quiet" onClick={signOut}>
            Sign out
          </button>
        </div>
      </header>

      <main className="main">
        {current === 'ask' && <AskView key={user.id} />}
        {current === 'library' && <LibraryView />}
        {current === 'admin' && <AdminView />}
      </main>
    </div>
  );
}
