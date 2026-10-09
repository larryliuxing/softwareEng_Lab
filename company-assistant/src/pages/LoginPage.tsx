import { useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { useStore } from '../store'
import { ROLE_LABEL } from '../types'

const featured = [
  ['赵衡', '系统管理员，管用户'],
  ['沈清和', '工会管理，发问卷'],
  ['周晚宁', '运转第一小组小组长'],
  ['陈予安', '检修第一小组小组长'],
  ['许南枝', '人力管理员，可改全体考勤'],
  ['韩叙', '运转车间部门负责人'],
  ['马晓东', '普通工会成员'],
] as const

export function LoginPage() {
  const { session, login, users, resetDemo, notify } = useStore()
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [show, setShow] = useState(false)
  const [error, setError] = useState('')

  if (session) return <Navigate to="/" replace />

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const message = login(name, password)
    setError(message ?? '')
  }

  return (
    <div className="login">
      <section className="login-aside">
        <div className="aside-seal">助</div>
        <p className="aside-kicker">铁路单位 · 内部使用</p>
        <h1>公司小助手</h1>
        <p className="login-lead">工会发放、出勤汇总、发票签章，三件事放在同一处办理。</p>
        <ol className="module-index">
          <li>
            <span>01</span>
            <div>
              <strong>工会助手</strong>
              <p>工会管理推送带图片的问卷，小组长看本组谁填了，填齐后出签收单。</p>
            </div>
          </li>
          <li>
            <span>02</span>
            <div>
              <strong>考勤统计</strong>
              <p>把内网导出和另一张表合在一起，看成每个人的出勤日历。</p>
            </div>
          </li>
          <li>
            <span>03</span>
            <div>
              <strong>发票助手</strong>
              <p>拖入未签章 XML。签章需要登录税务局，这一步先留框架。</p>
            </div>
          </li>
        </ol>
      </section>
      <section className="login-main">
        <form className="login-card" onSubmit={onSubmit}>
          <h2>登录</h2>
          <p className="lede">请自己输入姓名和密码。新账号和重置后的初始密码都是 888888。</p>
          <label className="field">
            <span>姓名</span>
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              autoComplete="username"
              placeholder="与用户管理里的姓名一致"
            />
          </label>
          <label className="field">
            <span className="password-line">
              密码
              <button type="button" className="text-btn" onClick={() => setShow((value) => !value)}>
                {show ? '隐藏' : '显示'}
              </button>
            </span>
            <input
              type={show ? 'text' : 'password'}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              placeholder="初始密码 888888"
            />
          </label>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="btn btn-primary btn-block">
            进入
          </button>
          <div className="demo-box">
            <p>演示人员，密码均为 888888。请手动输入，不能点名字登录。</p>
            <ul className="demo-list">
              {featured.map(([person, role]) => (
                <li key={person}>
                  <strong>{person}</strong>
                  <span>{role}</span>
                </li>
              ))}
            </ul>
            <details>
              <summary>其余 {users.length - featured.length} 人</summary>
              <p className="fine">
                {users
                  .filter((user) => !featured.some(([person]) => person === user.name))
                  .map((user) => `${user.name}（${user.roles.map((role) => ROLE_LABEL[role]).join('、')}）`)
                  .join('、')}
              </p>
            </details>
          </div>
          <button
            type="button"
            className="text-btn"
            onClick={() => {
              resetDemo()
              setName('')
              setPassword('')
              setError('')
              notify('已恢复演示数据')
            }}
          >
            恢复演示数据
          </button>
        </form>
      </section>
    </div>
  )
}
