import { useEffect, useState } from 'react';
import { notificationStreamUrl, type AppNotification } from '../api';
import { DOCUMENT_EVENT } from './shared';

const SHOW_MS = 6000;

/**
 * Listens to the server's notification stream and shows a short message when a document finishes
 * processing. Other views refresh on the same event instead of waiting for their next poll.
 */
export default function Notifications() {
  const [shown, setShown] = useState<(AppNotification & { key: number })[]>([]);

  useEffect(() => {
    const url = notificationStreamUrl();
    if (!url || typeof EventSource === 'undefined') return;
    const source = new EventSource(url);
    source.addEventListener('document', (event) => {
      const notification = JSON.parse((event as MessageEvent<string>).data) as AppNotification;
      window.dispatchEvent(new CustomEvent(DOCUMENT_EVENT, { detail: notification }));
      const key = Date.now() + Math.random();
      setShown((list) => [...list, { ...notification, key }]);
      window.setTimeout(() => setShown((list) => list.filter((n) => n.key !== key)), SHOW_MS);
    });
    return () => source.close();
  }, []);

  if (shown.length === 0) return null;
  return (
    <div className="toasts" role="status" aria-live="polite">
      {shown.map((n) => (
        <div key={n.key} className="toast" data-type={n.type}>
          {n.message}
        </div>
      ))}
    </div>
  );
}
