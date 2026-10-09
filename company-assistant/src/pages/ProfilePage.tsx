import { useState, type FormEvent } from 'react'
import { INITIAL_PASSWORD } from '../types'
import { useStore } from '../store'

export function ProfilePage() {
  const { session, changePassword, notify } = useStore()
  const [oldPassword, setOldPassword] = useState('')
  const [nextPassword, setNextPassword] = useState('')
  const [again, setAgain] = useState('')
  const [error, setError] = useState('')
  if (!session) return null

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (nextPassword !== again) {
      setError('两次输入的新密码不一致')
      return
    }
    const message = changePassword(oldPassword, nextPassword)
    if (message) {
      setError(message)
      return
    }
    setError('')
    setOldPassword('')
    setNextPassword('')
    setAgain('')
    notify('密码已更新')
  }

  return (
    <div className="page narrow">
      <header className="page-head">
        <h1>修改密码</h1>
        <p className="lede">
          {session.name}，当前{session.password === INITIAL_PASSWORD ? '仍是初始密码 888888' : '已不是初始密码'}。
        </p>
      </header>
      <form className="panel form-stack" onSubmit={onSubmit}>
        <label className="field">
          <span>原密码</span>
          <input type="password" value={oldPassword} onChange={(event) => setOldPassword(event.target.value)} />
        </label>
        <label className="field">
          <span>新密码</span>
          <input
            type="password"
            value={nextPassword}
            onChange={(event) => setNextPassword(event.target.value)}
            placeholder="至少 6 位"
          />
        </label>
        <label className="field">
          <span>再次输入新密码</span>
          <input type="password" value={again} onChange={(event) => setAgain(event.target.value)} />
        </label>
        {error && <p className="form-error">{error}</p>}
        <button type="submit" className="btn btn-primary">
          保存新密码
        </button>
      </form>
    </div>
  )
}
