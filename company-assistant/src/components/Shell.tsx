import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { INITIAL_PASSWORD, ROLE_LABEL, hasRole } from '../types'
import { useStore } from '../store'

const TITLES: Record<string, string> = {
  '/': '工作台',
  '/union': '工会助手',
  '/attendance': '考勤统计',
  '/invoice': '发票助手',
  '/users': '用户管理',
  '/profile': '修改密码',
}

export function Shell() {
  const { session, logout } = useStore()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  if (!session) return null

  const items = [
    { to: '/', label: '工作台', end: true, show: true },
    {
      to: '/union',
      label: '工会助手',
      show: hasRole(session, 'union_member') || hasRole(session, 'union_admin') || hasRole(session, 'union_leader'),
    },
    { to: '/attendance', label: '考勤统计', show: true },
    { to: '/invoice', label: '发票助手', show: true },
    { to: '/users', label: '用户管理', show: hasRole(session, 'admin') },
  ]

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">助</span>
          <span className="brand-text">
            <strong>公司小助手</strong>
            <span>内部事务</span>
          </span>
        </div>
        <nav className="nav">
          {items
            .filter((item) => item.show)
            .map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => (isActive ? 'nav-link is-active' : 'nav-link')}
              >
                {item.label}
              </NavLink>
            ))}
        </nav>
        <div className="side-foot">
          <div className="who">
            <strong>{session.name}</strong>
            <span>{session.employeeNo}</span>
          </div>
          <div className="role-row">
            {session.roles.map((role) => (
              <span key={role} className="chip chip-dark">
                {ROLE_LABEL[role]}
              </span>
            ))}
          </div>
          <div className="side-actions">
            <button type="button" className="text-btn" onClick={() => navigate('/profile')}>
              修改密码
            </button>
            <button
              type="button"
              className="text-btn"
              onClick={() => {
                logout()
                navigate('/login')
              }}
            >
              退出登录
            </button>
          </div>
        </div>
      </aside>
      <div className="content">
        <header className="topbar">
          <p className="crumb">{TITLES[pathname] ?? '公司小助手'}</p>
        </header>
        {session.password === INITIAL_PASSWORD && pathname !== '/profile' && (
          <div className="warn-bar">
            <span>你还在使用初始密码 888888。</span>
            <button type="button" className="text-btn" onClick={() => navigate('/profile')}>
              去修改
            </button>
          </div>
        )}
        <Outlet />
      </div>
    </div>
  )
}
