import { useMemo, useState } from 'react'
import { Modal } from '../components/Modal'
import { useStore, type UserDraft } from '../store'
import { INITIAL_PASSWORD, ROLE_LABEL, ROLE_ORDER, hasRole, type Role, type User } from '../types'

const emptyDraft = (): UserDraft => ({
  name: '',
  employeeNo: '',
  departmentId: '',
  unionGroupId: '',
  roles: ['union_member'],
  active: true,
})

export function UsersPage() {
  const store = useStore()
  const { session, users, departments, groups, notify } = store
  const [keyword, setKeyword] = useState('')
  const [deptFilter, setDeptFilter] = useState('all')
  const [roleFilter, setRoleFilter] = useState<'all' | Role>('all')
  const [draft, setDraft] = useState<UserDraft | null>(null)
  const [formError, setFormError] = useState('')
  const [deptName, setDeptName] = useState('')
  const [groupName, setGroupName] = useState('')
  const [confirm, setConfirm] = useState<{ user: User; kind: 'reset' | 'toggle' } | null>(null)

  const filtered = useMemo(() => {
    const text = keyword.trim()
    return users
      .filter((user) => (deptFilter === 'all' ? true : user.departmentId === deptFilter))
      .filter((user) => (roleFilter === 'all' ? true : user.roles.includes(roleFilter)))
      .filter((user) => (text ? user.name.includes(text) || user.employeeNo.toLowerCase().includes(text.toLowerCase()) : true))
      .sort((a, b) => a.employeeNo.localeCompare(b.employeeNo))
  }, [users, keyword, deptFilter, roleFilter])

  if (!session) return null
  if (!hasRole(session, 'admin')) {
    return (
      <div className="page">
        <p className="empty">只有系统管理员可以管理人员。</p>
      </div>
    )
  }

  function openCreate() {
    setFormError('')
    setDraft({
      ...emptyDraft(),
      departmentId: departments[0]?.id ?? '',
      unionGroupId: groups[0]?.id ?? '',
    })
  }

  function openEdit(user: User) {
    setFormError('')
    setDraft({
      id: user.id,
      name: user.name,
      employeeNo: user.employeeNo,
      departmentId: user.departmentId,
      unionGroupId: user.unionGroupId,
      roles: user.roles,
      active: user.active,
    })
  }

  function toggleRole(role: Role) {
    setDraft((current) => {
      if (!current) return current
      const roles = current.roles.includes(role)
        ? current.roles.filter((item) => item !== role)
        : [...current.roles, role]
      return { ...current, roles }
    })
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>用户管理</h1>
          <p className="lede">登录认的是姓名和密码。新建、重置之后密码都是 {INITIAL_PASSWORD}，由本人自己输入登录。</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={openCreate}>
          新增用户
        </button>
      </header>

      <section className="panel org-board">
        <div>
          <h2>部门</h2>
          <div className="chip-line">
            {departments.map((item) => (
              <span key={item.id} className="chip">
                {item.name}
              </span>
            ))}
          </div>
          <form
            className="inline-add"
            onSubmit={(event) => {
              event.preventDefault()
              const message = store.addDepartment(deptName)
              if (message) notify(message)
              else {
                setDeptName('')
                notify('已添加部门')
              }
            }}
          >
            <input value={deptName} onChange={(event) => setDeptName(event.target.value)} placeholder="新部门名称" />
            <button type="submit" className="btn btn-ghost btn-small">
              添加
            </button>
          </form>
        </div>
        <div>
          <h2>工会小组</h2>
          <div className="chip-line">
            {groups.map((item) => (
              <span key={item.id} className="chip">
                {item.name}
              </span>
            ))}
          </div>
          <form
            className="inline-add"
            onSubmit={(event) => {
              event.preventDefault()
              const message = store.addGroup(groupName)
              if (message) notify(message)
              else {
                setGroupName('')
                notify('已添加工会小组')
              }
            }}
          >
            <input value={groupName} onChange={(event) => setGroupName(event.target.value)} placeholder="新小组名称" />
            <button type="submit" className="btn btn-ghost btn-small">
              添加
            </button>
          </form>
        </div>
      </section>

      <div className="filters">
        <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索姓名或工号" />
        <select value={deptFilter} onChange={(event) => setDeptFilter(event.target.value)}>
          <option value="all">全部部门</option>
          {departments.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
            </option>
          ))}
        </select>
        <select value={roleFilter} onChange={(event) => setRoleFilter(event.target.value as 'all' | Role)}>
          <option value="all">全部身份</option>
          {ROLE_ORDER.map((role) => (
            <option key={role} value={role}>
              {ROLE_LABEL[role]}
            </option>
          ))}
        </select>
      </div>

      <div className="table-wrap panel">
        <table className="table">
          <thead>
            <tr>
              <th>姓名</th>
              <th>工号</th>
              <th>部门</th>
              <th>工会小组</th>
              <th>身份</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((user) => (
              <tr key={user.id}>
                <td>
                  {user.name}
                  {user.password === INITIAL_PASSWORD && <span className="badge badge-warn">初始密码</span>}
                </td>
                <td>{user.employeeNo}</td>
                <td>{departments.find((item) => item.id === user.departmentId)?.name}</td>
                <td>{groups.find((item) => item.id === user.unionGroupId)?.name}</td>
                <td>
                  <div className="chip-line">
                    {user.roles.map((role) => (
                      <span key={role} className="chip">
                        {ROLE_LABEL[role]}
                      </span>
                    ))}
                  </div>
                </td>
                <td>{user.active ? '在用' : '已停用'}</td>
                <td className="row-actions">
                  <button type="button" className="btn btn-ghost btn-small" onClick={() => openEdit(user)}>
                    编辑
                  </button>
                  <button type="button" className="btn btn-ghost btn-small" onClick={() => setConfirm({ user, kind: 'reset' })}>
                    重置密码
                  </button>
                  <button
                    type="button"
                    className="btn btn-ghost btn-small"
                    disabled={user.id === session.id}
                    onClick={() => setConfirm({ user, kind: 'toggle' })}
                  >
                    {user.active ? '停用' : '启用'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && <p className="empty">没有符合条件的人员。</p>}
      </div>
      <p className="fine">演示数据只存在这台浏览器里。正式使用要换成单位自己的账号系统。</p>

      {draft && (
        <Modal title={draft.id ? '编辑用户' : '新增用户'} onClose={() => setDraft(null)}>
          <form
            className="form-stack"
            onSubmit={(event) => {
              event.preventDefault()
              const message = store.saveUser(draft)
              if (message) {
                setFormError(message)
                return
              }
              notify(draft.id ? '已保存' : `已创建，初始密码 ${INITIAL_PASSWORD}`)
              setDraft(null)
            }}
          >
            <label className="field">
              <span>姓名</span>
              <input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
            </label>
            <label className="field">
              <span>工号</span>
              <input
                value={draft.employeeNo}
                onChange={(event) => setDraft({ ...draft, employeeNo: event.target.value })}
              />
            </label>
            <label className="field">
              <span>部门</span>
              <select
                value={draft.departmentId}
                onChange={(event) => setDraft({ ...draft, departmentId: event.target.value })}
              >
                {departments.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>工会小组</span>
              <select
                value={draft.unionGroupId}
                onChange={(event) => setDraft({ ...draft, unionGroupId: event.target.value })}
              >
                {groups.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <fieldset className="role-picker">
              <legend>身份</legend>
              {ROLE_ORDER.map((role) => (
                <label key={role} className={draft.roles.includes(role) ? 'choice is-on' : 'choice'}>
                  <input type="checkbox" checked={draft.roles.includes(role)} onChange={() => toggleRole(role)} />
                  <span>{ROLE_LABEL[role]}</span>
                </label>
              ))}
            </fieldset>
            <label className="check-line">
              <input
                type="checkbox"
                checked={draft.active}
                onChange={(event) => setDraft({ ...draft, active: event.target.checked })}
              />
              账号启用
            </label>
            <p className="fine">
              {draft.id ? '保存不会改密码。需要的话再用列表里的重置。' : `创建后初始密码为 ${INITIAL_PASSWORD}。`}
            </p>
            {formError && <p className="form-error">{formError}</p>}
            <button type="submit" className="btn btn-primary">
              保存
            </button>
          </form>
        </Modal>
      )}

      {confirm && (
        <Modal title={confirm.kind === 'reset' ? '重置密码' : confirm.user.active ? '停用账号' : '启用账号'} onClose={() => setConfirm(null)}>
          <p className="lede">
            {confirm.kind === 'reset'
              ? `把 ${confirm.user.name} 的密码改回 ${INITIAL_PASSWORD}？本人下次需要自己输入姓名和新密码。`
              : confirm.user.active
                ? `停用后 ${confirm.user.name} 将不能登录。`
                : `重新启用 ${confirm.user.name}？`}
          </p>
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" onClick={() => setConfirm(null)}>
              取消
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                if (confirm.kind === 'reset') {
                  store.resetPassword(confirm.user.id)
                  notify(`已把 ${confirm.user.name} 的密码重置为 ${INITIAL_PASSWORD}`)
                } else {
                  store.saveUser({
                    id: confirm.user.id,
                    name: confirm.user.name,
                    employeeNo: confirm.user.employeeNo,
                    departmentId: confirm.user.departmentId,
                    unionGroupId: confirm.user.unionGroupId,
                    roles: confirm.user.roles,
                    active: !confirm.user.active,
                  })
                  notify(confirm.user.active ? '已停用' : '已启用')
                }
                setConfirm(null)
              }}
            >
              确定
            </button>
          </div>
        </Modal>
      )}
    </div>
  )
}
