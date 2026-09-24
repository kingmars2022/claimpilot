import type { Department } from '../api';

interface Props {
  departments: Department[];
  /** Selected department ids; empty means the whole company. */
  value: number[];
  onChange: (value: number[]) => void;
  legend: string;
}

/** Chooses who can see a document: the whole company, or selected departments. */
export default function VisibilityPicker({ departments, value, onChange, legend }: Props) {
  const restricted = value.length > 0;

  function toggle(id: number, checked: boolean) {
    const next = checked ? [...value, id] : value.filter((v) => v !== id);
    onChange(next);
  }

  return (
    <fieldset className="visibility">
      <legend>{legend}</legend>
      <label className="choice">
        <input type="radio" checked={!restricted} onChange={() => onChange([])} />
        Whole company
      </label>
      <span className="choice-divider small muted">or only</span>
      {departments.map((dept) => (
        <label key={dept.id} className="choice">
          <input
            type="checkbox"
            checked={value.includes(dept.id)}
            onChange={(e) => toggle(dept.id, e.target.checked)}
          />
          {dept.name}
        </label>
      ))}
    </fieldset>
  );
}

export function visibilityLabel(visibleTo: Department[]) {
  return visibleTo.length === 0 ? 'Whole company' : visibleTo.map((d) => d.name).join(', ');
}
