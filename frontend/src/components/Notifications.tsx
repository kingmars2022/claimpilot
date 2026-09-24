import { useEffect, useState } from 'react';
import { api, type AppNotification } from '../api';
import { DOCUMENT_EVENT } from './shared';

const SHOW_MS = 6000;
const RECONNECT_MS = 5000;

/**
 * Listens to the server's notification stream and shows a short message when a document finishes
 * processing. Other views refresh on the same event instead of waiting for their next poll.
 */
export default function Notifications() {
  const [shown, setShown] = useState<(AppNotification & { key: number })[]>([]);

  useEffect(() => {
    if (typeof EventSource === 'undefined') return;
    let source: EventSource | null = null;
    let retry: number | undefined;
    let stopped = false;

    // Tickets last a minute, so a dropped connection is reopened with a fresh one.
    async function open() {
      try {
        const url = await api.notificationStreamUrl();
        if (stopped) return;
        source = new EventSource(url);
        source.addEventListener('document', (event) => {
          const notification = JSON.parse((event as MessageEvent<string>).data) as AppNotification;
          window.dispatchEvent(new CustomEvent(DOCUMENT_EVENT, { detail: notification }));
          const key = Date.now() + Math.random();
          setShown((list) => [...list, { ...notification, key }]);
          window.setTimeout(() => setShown((list) => list.filter((n) => n.key !== key)), SHOW_MS);
        });
        source.onerror = () => {
          source?.close();
          if (!stopped) retry = window.setTimeout(() => void open(), RECONNECT_MS);
        };
      } catch {
        if (!stopped) retry = window.setTimeout(() => void open(), RECONNECT_MS);
      }
    }

    void open();
    return () => {
      stopped = true;
      window.clearTimeout(retry);
      source?.close();
    };
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
