import { useEffect, useState, type FormEvent } from 'react';
import { api, type Child, type Custody } from '../api';
import { errorText } from './shared';

const CUSTODY: { value: Custody; label: string }[] = [
  { value: 'TOGETHER', label: 'Lives with me and my spouse (their other parent)' },
  { value: 'SOLE_ME', label: 'Separated: I have custody' },
  { value: 'SOLE_OTHER_PARENT', label: 'Separated: the other parent has custody' },
  { value: 'JOINT', label: 'Separated: joint custody' },
];

const NEW_CHILD: Child = {
  id: null,
  fullName: '',
  dateOfBirth: null,
  custody: 'TOGETHER',
  otherParentName: null,
  otherParentDateOfBirth: null,
};

/**
 * The member's children. A child's name and birth date go on their claims, and their custody
 * decides which plan pays first for them; children of two relationships can differ.
 */
export default function ChildrenEditor() {
  const [children, setChildren] = useState<Child[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.children().then(setChildren, (err) => setError(errorText(err)));
  }, []);

  function change(index: number, update: Partial<Child>) {
    setChildren(children.map((c, i) => (i === index ? { ...c, ...update } : c)));
    setMessage(null);
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    try {
      setChildren(await api.saveChildren(children));
      setMessage('Saved.');
      setError(null);
    } catch (err) {
      setError(errorText(err));
    }
  }

  return (
    <section className="children">
      <h2>Children</h2>
      <p className="muted small">
        A child's name and birth date go on their claims. For separated parents, custody decides which plan pays
        first; your spouse above is then the child's step-parent.
      </p>
      <form className="form" onSubmit={save}>
        {children.map((child, index) => (
          <fieldset key={child.id ?? `new-${index}`} className="child">
            <legend>{child.fullName || 'New child'}</legend>
            <label>
              Full name
              <input value={child.fullName} required onChange={(e) => change(index, { fullName: e.target.value })} />
            </label>
            <label>
              Date of birth
              <input
                type="date"
                value={child.dateOfBirth ?? ''}
                onChange={(e) => change(index, { dateOfBirth: e.target.value || null })}
              />
            </label>
            <label>
              Custody
              <select
                value={child.custody}
                onChange={(e) => change(index, { custody: e.target.value as Custody })}
              >
                {CUSTODY.map((c) => (
                  <option key={c.value} value={c.value}>
                    {c.label}
                  </option>
                ))}
              </select>
            </label>
            {child.custody !== 'TOGETHER' && (
              <>
                <label>
                  Other parent (as on their plan)
                  <input
                    value={child.otherParentName ?? ''}
                    onChange={(e) => change(index, { otherParentName: e.target.value || null })}
                  />
                </label>
                <label>
                  Other parent's date of birth
                  <input
                    type="date"
                    value={child.otherParentDateOfBirth ?? ''}
                    onChange={(e) => change(index, { otherParentDateOfBirth: e.target.value || null })}
                  />
                </label>
              </>
            )}
            <button
              type="button"
              className="link"
              onClick={() => setChildren(children.filter((_, i) => i !== index))}
            >
              Remove
            </button>
          </fieldset>
        ))}
        {message && <p className="small">{message}</p>}
        {error && <p className="error">{error}</p>}
        <div className="actions">
          <button type="button" onClick={() => setChildren([...children, { ...NEW_CHILD }])}>
            Add a child
          </button>
          <button type="submit" className="primary">
            Save children
          </button>
        </div>
      </form>
    </section>
  );
}
