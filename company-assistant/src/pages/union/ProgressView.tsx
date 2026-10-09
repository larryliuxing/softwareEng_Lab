import { useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { buildSignRows, describeAnswers, downloadSignSheet, type SignRow } from '../../lib/excel'
import { formatChinese, formatDateTime } from '../../lib/format'
import { useStore } from '../../store'

export function ProgressView() {
  const store = useStore()
  const { session, users, groups, departments, questionnaires, answers } = store
  const published = questionnaires.filter((item) => item.status !== 'draft')
  const [currentId, setCurrentId] = useState(published[0]?.id ?? '')
  const [open, setOpen] = useState(false)
  const questionnaire = published.find((item) => item.id === currentId) ?? null
  const group = groups.find((item) => item.id === session?.unionGroupId) ?? null

  const members = useMemo(() => {
    if (!session || !group) return []
    return users
      .filter((user) => user.active && user.unionGroupId === group.id && user.roles.includes('union_member'))
      .sort((a, b) => a.employeeNo.localeCompare(b.employeeNo))
  }, [users, session, group])

  if (!session || !group || !questionnaire) {
    return <p className="empty">本组还没有可汇总的问卷。</p>
  }

  const rows = members.map((user) => {
    const answer = answers.find((item) => item.questionnaireId === questionnaire.id && item.userId === user.id)
    return { user, answer }
  })
  const missing = rows.filter((row) => !row.answer).length
  const ready = members.length > 0 && missing === 0
  const signRows: SignRow[] = buildSignRows(questionnaire, group, users, answers, departments)

  return (
    <div className="panel">
      <div className="filters">
        <label className="field">
          <span>问卷</span>
          <select value={currentId} onChange={(event) => setCurrentId(event.target.value)}>
            {published.map((item) => (
              <option key={item.id} value={item.id}>
                {item.title}
              </option>
            ))}
          </select>
        </label>
        <div className="summary-line">
          <strong>
            {group.name} · 已填 {members.length - missing} / {members.length}
          </strong>
          <span className="fine">只显示本组工会成员。</span>
        </div>
      </div>
      <div className="meter" aria-hidden="true">
        <span style={{ width: `${members.length ? ((members.length - missing) / members.length) * 100 : 0}%` }} />
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>姓名</th>
              <th>工号</th>
              <th>状态</th>
              <th>提交时间</th>
              <th>填写内容</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.user.id} className={row.answer ? '' : 'is-miss'}>
                <td>{row.user.name}</td>
                <td>{row.user.employeeNo}</td>
                <td>
                  <span className={row.answer ? 'badge badge-ok' : 'badge badge-warn'}>{row.answer ? '已填写' : '未填写'}</span>
                </td>
                <td>{row.answer ? formatDateTime(row.answer.submittedAt) : '—'}</td>
                <td>{describeAnswers(questionnaire.questions, row.answer)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="confirm-line">
        <button type="button" className="btn btn-primary" disabled={!ready} onClick={() => setOpen(true)}>
          生成签收单
        </button>
        <p className="fine">
          {ready
            ? '本组都填完了，可以下载或打印。正式表格样式等样例到了再换。'
            : `还有 ${missing} 人没填，填齐后才能生成。`}
        </p>
      </div>
      {open && (
        <SignDialog
          title={questionnaire.title}
          itemName={questionnaire.itemName}
          deadline={questionnaire.deadline}
          groupName={group.name}
          leaderName={session.name}
          rows={signRows}
          onClose={() => setOpen(false)}
          onDownload={() => downloadSignSheet(questionnaire, group, signRows, session.name)}
        />
      )}
    </div>
  )
}

export function OverviewView() {
  const { groups, users, questionnaires, answers } = useStore()
  const published = questionnaires.filter((item) => item.status !== 'draft')
  const [currentId, setCurrentId] = useState(published[0]?.id ?? '')
  const questionnaire = published.find((item) => item.id === currentId)
  if (!questionnaire) return <p className="empty">还没有推送过的问卷。</p>

  return (
    <div className="panel">
      <label className="field">
        <span>问卷</span>
        <select value={currentId} onChange={(event) => setCurrentId(event.target.value)}>
          {published.map((item) => (
            <option key={item.id} value={item.id}>
              {item.title}
            </option>
          ))}
        </select>
      </label>
      <p className="fine">这里只看各小组填了多少人，不展开每个人的选择。个人结果由小组长在本组查看。</p>
      <div className="overview-list">
        {groups.map((group) => {
          const members = users.filter(
            (user) => user.active && user.unionGroupId === group.id && user.roles.includes('union_member'),
          )
          const done = members.filter((user) =>
            answers.some((answer) => answer.questionnaireId === questionnaire.id && answer.userId === user.id),
          ).length
          const ratio = members.length ? done / members.length : 0
          return (
            <article key={group.id}>
              <div>
                <strong>{group.name}</strong>
                <span>
                  {done} / {members.length}
                </span>
              </div>
              <div className="meter">
                <span style={{ width: `${ratio * 100}%` }} />
              </div>
            </article>
          )
        })}
      </div>
    </div>
  )
}

function SignDialog({
  title,
  itemName,
  deadline,
  groupName,
  leaderName,
  rows,
  onClose,
  onDownload,
}: {
  title: string
  itemName: string
  deadline: string
  groupName: string
  leaderName: string
  rows: SignRow[]
  onClose: () => void
  onDownload: () => void
}) {
  return createPortal(
    <div className="mask sheet-mask" role="presentation" onMouseDown={onClose}>
      <div className="sheet-dialog" onMouseDown={(event) => event.stopPropagation()}>
        <div className="row-actions no-print">
          <button type="button" className="btn btn-primary" onClick={onDownload}>
            下载 Excel
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => {
              document.body.classList.add('printing-sheet')
              window.print()
              window.setTimeout(() => document.body.classList.remove('printing-sheet'), 400)
            }}
          >
            打印
          </button>
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            关闭
          </button>
        </div>
        <article className="sheet">
          <div className="sheet-seal">临时</div>
          <h2>工会物品签收单</h2>
          <p className="sheet-meta">
            <span>事项：{title}</span>
            <span>物品：{itemName}</span>
            <span>小组：{groupName}</span>
            <span>截止：{formatChinese(deadline)}</span>
          </p>
          <p className="sheet-note">临时版式。正式的列、盖章和页眉以后续样表为准。签字栏留给本人手签。</p>
          <table>
            <thead>
              <tr>
                <th>序号</th>
                <th>姓名</th>
                <th>工号</th>
                <th>部门</th>
                <th>签收内容</th>
                <th>数量</th>
                <th>签字</th>
                <th>日期</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.employeeNo}>
                  <td>{row.index}</td>
                  <td>{row.name}</td>
                  <td>{row.employeeNo}</td>
                  <td>{row.department}</td>
                  <td>{row.content}</td>
                  <td>{row.quantity}</td>
                  <td />
                  <td />
                </tr>
              ))}
            </tbody>
          </table>
          <p className="sheet-foot">
            小组长：{leaderName}　　小组长签字：　　　　日期：　　　　工会经办：
          </p>
        </article>
      </div>
    </div>,
    document.body,
  )
}
