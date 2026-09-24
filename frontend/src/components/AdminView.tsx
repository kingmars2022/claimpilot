import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, ROLE_LABELS, type Department, type Role, type User, type UserInput } from '../api';
import { useSession } from '../auth';

const ROLES: Role[] = ['EMPLOYEE', 'KNOWLEDGE_MANAGER', 'ADMIN'];

const EMPTY_USER: UserInput = { username: '', displayName: '', password: '', role: 'EMPLOYEE', departmentId: null };

export default function AdminView() {
  const { user: me } = useSession();
  const [users, setUsers] = useState<User[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [newUser, setNewUser] = useState<UserInput>(EMPTY_USER);
  const [editing, setEditing] = useState<{ id: number; input: UserInput } | null>(null);
  const [newDept, setNewDept] = useState({ code: '', name: '' });

  const refresh = useCallback(async () => {
    try {
      const [u, d] = await Promise.all([api.users(), api.departments()]);
      setUsers(u);
      setDepartments(d);
    } catch (err) {
      setError(message(err));
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function run(action: () => Promise<unknown>, after?: () => void) {
    try {
      await action();
      setError(null);
      after?.();
    } catch (err) {
      setError(message(err));
    }
    await refresh();
  }

  function createUser(event: FormEvent) {
    event.preventDefault();
    void run(() => api.createUser(newUser), () => setNewUser(EMPTY_USER));
  }

  function createDepartment(event: FormEvent) {
    event.preventDefault();
    void run(() => api.createDepartment(newDept.code, newDept.name), () => setNewDept({ code: '', name: '' }));
  }

  return (
    <div className="library admin">
      <div className="library-head">
        <h1>Admin</h1>
        <p className="muted">
          People, roles and departments. A department change applies to the next question the person asks.
        </p>
      </div>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      <section className="admin-section">
        <h2>Users</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">Username</th>
                <th scope="col">Name</th>
                <th scope="col">Role</th>
                <th scope="col">Department</th>
                <th scope="col">
                  <span className="visually-hidden">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) =>
                editing?.id === user.id ? (
                  <tr key={user.id} className="editing">
                    <td className="file">{user.username}</td>
                    <td>
                      <input
                        aria-label="Name"
                        value={editing.input.displayName}
                        onChange={(e) => setEditing({ ...editing, input: { ...editing.input, displayName: e.target.value } })}
                      />
                      <input
                        aria-label="New password"
                        type="password"
                        placeholder="New password (optional)"
                        value={editing.input.password ?? ''}
                        onChange={(e) => setEditing({ ...editing, input: { ...editing.input, password: e.target.value } })}
                      />
                    </td>
                    <td>
                      <RoleSelect
                        value={editing.input.role}
                        onChange={(role) => setEditing({ ...editing, input: { ...editing.input, role } })}
                      />
                    </td>
                    <td>
                      <DepartmentSelect
                        departments={departments}
                        value={editing.input.departmentId}
                        onChange={(departmentId) =>
                          setEditing({ ...editing, input: { ...editing.input, departmentId } })
                        }
                      />
                    </td>
                    <td className="actions">
                      <button
                        type="button"
                        className="primary small-button"
                        onClick={() => void run(() => api.updateUser(user.id, editing.input), () => setEditing(null))}
                      >
                        Save
                      </button>
                      <button type="button" className="quiet" onClick={() => setEditing(null)}>
                        Cancel
                      </button>
                    </td>
                  </tr>
                ) : (
                  <tr key={user.id}>
                    <td className="file">{user.username}</td>
                    <td>{user.displayName}</td>
                    <td>{ROLE_LABELS[user.role]}</td>
                    <td>{user.department?.name ?? <span className="muted">None</span>}</td>
                    <td className="actions">
                      <button
                        type="button"
                        className="quiet"
                        onClick={() =>
                          setEditing({
                            id: user.id,
                            input: {
                              displayName: user.displayName,
                              password: '',
                              role: user.role,
                              departmentId: user.department?.id ?? null,
                            },
                          })
                        }
                      >
                        Edit
                      </button>
                      {user.id !== me?.id && (
                        <button
                          type="button"
                          className="quiet"
                          onClick={() => {
                            if (window.confirm(`Delete ${user.username}? Their conversations stay in the history store.`)) {
                              void run(() => api.deleteUser(user.id));
                            }
                          }}
                        >
                          Delete
                        </button>
                      )}
                    </td>
                  </tr>
                ),
              )}
            </tbody>
          </table>
        </div>

        <form className="inline-form" onSubmit={createUser}>
          <h3>Add a user</h3>
          <input
            aria-label="Username"
            placeholder="Username"
            value={newUser.username}
            onChange={(e) => setNewUser({ ...newUser, username: e.target.value })}
            required
          />
          <input
            aria-label="Name"
            placeholder="Full name"
            value={newUser.displayName}
            onChange={(e) => setNewUser({ ...newUser, displayName: e.target.value })}
            required
          />
          <input
            aria-label="Password"
            type="password"
            placeholder="Password (8+ characters)"
            minLength={8}
            value={newUser.password}
            onChange={(e) => setNewUser({ ...newUser, password: e.target.value })}
            required
          />
          <RoleSelect value={newUser.role} onChange={(role) => setNewUser({ ...newUser, role })} />
          <DepartmentSelect
            departments={departments}
            value={newUser.departmentId}
            onChange={(departmentId) => setNewUser({ ...newUser, departmentId })}
          />
          <button type="submit" className="primary">
            Add user
          </button>
        </form>
      </section>

      <section className="admin-section">
        <h2>Departments</h2>
        <ul className="department-list">
          {departments.map((dept) => (
            <li key={dept.id}>
              {dept.name} <code>{dept.code}</code>
            </li>
          ))}
        </ul>
        <form className="inline-form" onSubmit={createDepartment}>
          <h3>Add a department</h3>
          <input
            aria-label="Code"
            placeholder="Code, e.g. LEGAL"
            pattern="[A-Za-z0-9_]+"
            value={newDept.code}
            onChange={(e) => setNewDept({ ...newDept, code: e.target.value })}
            required
          />
          <input
            aria-label="Department name"
            placeholder="Name, e.g. Legal"
            value={newDept.name}
            onChange={(e) => setNewDept({ ...newDept, name: e.target.value })}
            required
          />
          <button type="submit" className="primary">
            Add department
          </button>
        </form>
      </section>
    </div>
  );
}

function RoleSelect({ value, onChange }: { value: Role; onChange: (role: Role) => void }) {
  return (
    <select aria-label="Role" value={value} onChange={(e) => onChange(e.target.value as Role)}>
      {ROLES.map((role) => (
        <option key={role} value={role}>
          {ROLE_LABELS[role]}
        </option>
      ))}
    </select>
  );
}

function DepartmentSelect({
  departments,
  value,
  onChange,
}: {
  departments: Department[];
  value: number | null;
  onChange: (id: number | null) => void;
}) {
  return (
    <select
      aria-label="Department"
      value={value ?? ''}
      onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))}
    >
      <option value="">No department</option>
      {departments.map((dept) => (
        <option key={dept.id} value={dept.id}>
          {dept.name}
        </option>
      ))}
    </select>
  );
}

function message(err: unknown) {
  return err instanceof Error ? err.message : String(err);
}
