import { useState } from 'react'
import { hasRole } from '../types'
import { useStore } from '../store'
import { FillView } from './union/FillView'
import { ManageView } from './union/ManageView'
import { OverviewView, ProgressView } from './union/ProgressView'

type Tab = 'fill' | 'manage' | 'progress' | 'overview'

export function UnionPage() {
  const { session } = useStore()
  const canFill = hasRole(session, 'union_member')
  const canManage = hasRole(session, 'union_admin')
  const canLead = hasRole(session, 'union_leader')
  const tabs: { id: Tab; label: string }[] = [
    canFill ? { id: 'fill', label: '待我填写' } : null,
    canManage ? { id: 'manage', label: '问卷管理' } : null,
    canLead ? { id: 'progress', label: '本组进度' } : null,
    canManage ? { id: 'overview', label: '各小组进度' } : null,
  ].filter((item): item is { id: Tab; label: string } => item !== null)
  const [tab, setTab] = useState<Tab>(tabs[0]?.id ?? 'fill')
  const current = tabs.some((item) => item.id === tab) ? tab : tabs[0]?.id

  if (!session) return null
  if (tabs.length === 0) {
    return (
      <div className="page">
        <p className="empty">当前身份没有工会助手的权限。</p>
      </div>
    )
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>工会助手</h1>
          <p className="lede">发放食品和物品时，工会管理把问卷推给全体成员，小组长核对本组后生成签收单。</p>
        </div>
      </header>
      <div className="tabs" role="tablist">
        {tabs.map((item) => (
          <button
            key={item.id}
            type="button"
            role="tab"
            aria-selected={current === item.id}
            className={current === item.id ? 'tab is-on' : 'tab'}
            onClick={() => setTab(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>
      {current === 'fill' && <FillView />}
      {current === 'manage' && <ManageView />}
      {current === 'progress' && <ProgressView />}
      {current === 'overview' && <OverviewView />}
    </div>
  )
}
