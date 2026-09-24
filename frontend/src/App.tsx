import { useState } from 'react';
import AskView from './components/AskView';
import LibraryView from './components/LibraryView';

type View = 'ask' | 'library';

const TABS: { view: View; label: string }[] = [
  { view: 'ask', label: 'Ask' },
  { view: 'library', label: 'Library' },
];

export default function App() {
  const [view, setView] = useState<View>('ask');

  return (
    <div className="shell">
      <header className="topbar">
        <span className="wordmark">CompanyBrain</span>

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
      </header>

      <main className="main">{view === 'ask' ? <AskView /> : <LibraryView />}</main>
    </div>
  );
}
