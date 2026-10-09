import { Link } from 'react-router-dom'
import { baseAttendance } from '../lib/attendance'
import { daysInMonth, formatChinese, greeting, isPastDeadline, todayStamp, weekdayLabel, ymd } from '../lib/format'
import { useStore } from '../store'
import { ROLE_LABEL, hasRole } from '../types'

export function HomePage() {
  const { session, users, departments, groups, questionnaires, answers, overrides, invoices } = useStore()
  if (!session) return null
  const today = todayStamp()
  const department = departments.find((item) => item.id === session.departmentId)
  const group = groups.find((item) => item.id === session.unionGroupId)
  const openForms = questionnaires.filter((item) => item.status === 'published' && !isPastDeadline(item.deadline))
  const mineMissing = openForms.filter(
    (item) => !answers.some((answer) => answer.questionnaireId === item.id && answer.userId === session.id),
  )

  const leaderGaps = hasRole(session, 'union_leader')
    ? openForms.map((item) => {
        const members = users.filter(
          (user) => user.active && user.unionGroupId === session.unionGroupId && user.roles.includes('union_member'),
        )
        const missing = members.filter(
          (user) => !answers.some((answer) => answer.questionnaireId === item.id && answer.userId === user.id),
        )
        return { title: item.title, missing: missing.length, total: members.length }
      })
    : []

  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth() + 1
  const totalDays = daysInMonth(year, month)
  let abnormal = 0
  for (let day = 1; day <= totalDays; day += 1) {
    const date = ymd(year, month, day)
    const override = overrides[`${session.id}|${date}`]
    const status = override?.status ?? baseAttendance(session.id, date).status
    if (status === 'leave' || status === 'trip' || status === 'local') abnormal += 1
  }

  return (
    <div className="page">
      <section className="greet">
        <div>
          <p className="kicker">{greeting()}</p>
          <h1>{session.name}</h1>
          <p className="lede">
            {department?.name} · {group?.name}
          </p>
          <div className="role-row">
            {session.roles.map((role) => (
              <span key={role} className="chip">
                {ROLE_LABEL[role]}
              </span>
            ))}
          </div>
        </div>
        <div className="greet-date">
          <span className="num">{formatChinese(today)}</span>
          <span>{weekdayLabel(today)}</span>
        </div>
      </section>

      <section className="module-grid">
        <Link to="/union" className="module-card">
          <span className="idx">01</span>
          <h2>工会助手</h2>
          <p>发放食品、劳保时，由工会管理出问卷，小组长核对后生成签收单。</p>
          <p className="stat">
            <b className="num">{hasRole(session, 'union_member') ? mineMissing.length : '—'}</b>
            份待我填写
          </p>
        </Link>
        <Link to="/attendance" className="module-card">
          <span className="idx">02</span>
          <h2>考勤统计</h2>
          <p>内网导出的请假、出差、市内公出，和另一张表合并成出勤日历。</p>
          <p className="stat">
            <b className="num">{abnormal}</b>
            天本月异常
          </p>
        </Link>
        <Link to="/invoice" className="module-card">
          <span className="idx">03</span>
          <h2>发票助手</h2>
          <p>拖入未签章 XML。数字签名要登录电子税务局，目前只搭好流程。</p>
          <p className="stat">
            <b className="num">{invoices.length}</b>
            个待签章文件
          </p>
        </Link>
      </section>

      <section className="todo-grid">
        <article className="panel">
          <h2>待办</h2>
          {mineMissing.length === 0 && leaderGaps.every((item) => item.missing === 0) ? (
            <p className="empty">目前没有要你填写或催填的问卷。</p>
          ) : (
            <ul className="todo-list">
              {mineMissing.map((item) => (
                <li key={item.id}>
                  <span>待填写</span>
                  <strong>{item.title}</strong>
                  <em>截止 {formatChinese(item.deadline)}</em>
                </li>
              ))}
              {leaderGaps
                .filter((item) => item.missing > 0)
                .map((item) => (
                  <li key={item.title}>
                    <span>本组未齐</span>
                    <strong>{item.title}</strong>
                    <em>
                      还有 {item.missing} / {item.total} 人没填
                    </em>
                  </li>
                ))}
            </ul>
          )}
        </article>
        <article className="panel">
          <h2>你能办什么</h2>
          <ul className="ability">
            {hasRole(session, 'admin') && <li>维护人员、部门、工会小组，并把密码重置为 888888。</li>}
            {hasRole(session, 'union_admin') && <li>编写单选或多选问卷，题目和选项都可以配图，再推送给全体工会成员。</li>}
            {hasRole(session, 'union_leader') && <li>查看本组谁填了、谁没填。全部填完后生成签收单，下载或打印。</li>}
            {hasRole(session, 'union_member') && <li>填写推送给你的问卷。截止前可以改答案。</li>}
            {hasRole(session, 'hr_admin') && <li>查看全体出勤日历，并修订某一天的状态。</li>}
            {hasRole(session, 'dept_head') && <li>查看本部门每个人的出勤，不能修改。</li>}
            {!hasRole(session, 'hr_admin') && !hasRole(session, 'dept_head') && <li>考勤只能看自己的。</li>}
            <li>发票签章还不能真正加数字签名，页面只保留上传和税务局登录的位置。</li>
          </ul>
        </article>
      </section>
    </div>
  )
}
