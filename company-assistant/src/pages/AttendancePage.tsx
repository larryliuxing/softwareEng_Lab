import { useEffect, useMemo, useState } from 'react'
import { baseAttendance } from '../lib/attendance'
import { readWorkbook } from '../lib/excel'
import { daysInMonth, formatChinese, formatDateTime, monthCells, todayStamp, weekdayLabel, ymd } from '../lib/format'
import { useStore } from '../store'
import { STATUS_LABEL, STATUS_SHORT, hasRole, type DayStatus, type User } from '../types'

const STATUSES: DayStatus[] = ['work', 'leave', 'trip', 'local', 'rest']

function monthAbnormal(userId: string, year: number, month: number, overrides: ReturnType<typeof useStore>['overrides']) {
  let count = 0
  for (let day = 1; day <= daysInMonth(year, month); day += 1) {
    const date = ymd(year, month, day)
    const status = overrides[`${userId}|${date}`]?.status ?? baseAttendance(userId, date).status
    if (status === 'leave' || status === 'trip' || status === 'local') count += 1
  }
  return count
}

export function AttendancePage() {
  const store = useStore()
  const { session, users, departments, overrides, imports, notify } = store
  const now = new Date()
  const [cursor, setCursor] = useState({ year: now.getFullYear(), month: now.getMonth() + 1 })
  const [focus, setFocus] = useState<DayStatus | 'all'>('all')
  const [keyword, setKeyword] = useState('')
  const [over, setOver] = useState<'abnormal' | 'supplement' | null>(null)

  const visible = useMemo(() => {
    if (!session) return []
    const active = users.filter((user) => user.active || user.id === session.id)
    if (hasRole(session, 'hr_admin')) return active
    if (hasRole(session, 'dept_head')) return active.filter((user) => user.departmentId === session.departmentId)
    return active.filter((user) => user.id === session.id)
  }, [session, users])

  const [personId, setPersonId] = useState(session?.id ?? '')
  const [selectedDate, setSelectedDate] = useState<string | null>(todayStamp())

  useEffect(() => {
    if (!visible.some((user) => user.id === personId)) setPersonId(visible[0]?.id ?? '')
  }, [visible, personId])

  const person = visible.find((user) => user.id === personId) ?? null
  const canEdit = hasRole(session, 'hr_admin')
  const selfOnly = !!session && !hasRole(session, 'hr_admin') && !hasRole(session, 'dept_head')
  const bothReady = !!imports.abnormal && !!imports.supplement

  const record = useMemo(() => {
    if (!person || !selectedDate) return null
    const base = baseAttendance(person.id, selectedDate)
    const override = overrides[`${person.id}|${selectedDate}`]
    return {
      status: override?.status ?? base.status,
      source: override ? '人工修订' : base.source,
      note: override?.note ?? '',
      override,
    }
  }, [person, selectedDate, overrides])

  const [editStatus, setEditStatus] = useState<DayStatus>('work')
  const [editNote, setEditNote] = useState('')
  useEffect(() => {
    if (!record) return
    setEditStatus(record.status)
    setEditNote(record.note)
  }, [record])

  if (!session) return null

  const cells = monthCells(cursor.year, cursor.month)
  const counts = person
    ? STATUSES.map((status) => ({
        status,
        count: cells.filter((day) => {
          if (!day) return false
          const date = ymd(cursor.year, cursor.month, day)
          const value = overrides[`${person.id}|${date}`]?.status ?? baseAttendance(person.id, date).status
          return value === status
        }).length,
      }))
    : []

  async function takeFile(slot: 'abnormal' | 'supplement', file: File | undefined) {
    if (!file) return
    const lower = file.name.toLowerCase()
    if (!lower.endsWith('.xlsx') && !lower.endsWith('.xls')) {
      notify('请放入 Excel 文件（.xlsx 或 .xls）')
      return
    }
    try {
      const preview = await readWorkbook(file)
      store.setImport(slot, preview)
      notify(`已读入${file.name}`)
    } catch (error) {
      notify(error instanceof Error ? error.message : '这张表读不了')
    }
  }

  function shiftMonth(delta: number) {
    const date = new Date(cursor.year, cursor.month - 1 + delta, 1)
    setCursor({ year: date.getFullYear(), month: date.getMonth() + 1 })
    setSelectedDate(null)
  }

  const scope = hasRole(session, 'hr_admin')
    ? '你是人力管理员，可以查看全体，也可以改某一天。'
    : hasRole(session, 'dept_head')
      ? `你是部门负责人，只能查看${departments.find((item) => item.id === session.departmentId)?.name ?? '本部门'}，不能修改。`
      : '你只能查看自己的出勤。'

  const filteredPeople = visible.filter((user) => {
    const text = keyword.trim()
    if (!text) return true
    return user.name.includes(text) || user.employeeNo.toLowerCase().includes(text.toLowerCase())
  })

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>考勤统计</h1>
          <p className="lede">{scope}</p>
        </div>
      </header>

      <section className="import-grid">
        <DropSlot
          title="表一 · 内网考勤导出"
          hint="请假、出差、市内公出等从内网导出的表。"
          fileName={imports.abnormal?.fileName}
          active={over === 'abnormal'}
          onOver={(value) => setOver(value ? 'abnormal' : null)}
          onFile={(file) => takeFile('abnormal', file)}
        />
        <DropSlot
          title="表二 · 出勤补充表"
          hint="需要和表一合并的另一张表。具体列等样表到了再定。"
          fileName={imports.supplement?.fileName}
          active={over === 'supplement'}
          onOver={(value) => setOver(value ? 'supplement' : null)}
          onFile={(file) => takeFile('supplement', file)}
        />
      </section>

      <div className={bothReady ? 'note-bar is-ready' : 'note-bar'}>
        {bothReady ? (
          <p>
            两张表都已读入（{imports.abnormal?.rowCount} 行 / {imports.supplement?.rowCount} 行）。表头还没绑定到姓名、日期和请假类型，下面的日历仍按演示规则展示。绑定规则等你给样表后再接上，人工修订会保留。
          </p>
        ) : (
          <p>先把两张 Excel 放上来。日历可以先看版式，当前数字是演示规则，不是正式合并结果。</p>
        )}
        {(imports.abnormal || imports.supplement) && (
          <button type="button" className="btn btn-ghost btn-small" onClick={() => store.clearImports()}>
            清除导入
          </button>
        )}
      </div>

      {(imports.abnormal || imports.supplement) && (
        <div className="preview-grid">
          <Preview title="表一预览" preview={imports.abnormal} />
          <Preview title="表二预览" preview={imports.supplement} />
        </div>
      )}

      <section className={selfOnly ? 'cal-layout is-self' : 'cal-layout'}>
        {!selfOnly && (
          <aside className="people panel">
            <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="找人" />
            {[...departments]
              .sort((a, b) => Number(b.id === session.departmentId) - Number(a.id === session.departmentId))
              .filter((dept) => filteredPeople.some((user) => user.departmentId === dept.id))
              .map((dept) => (
                <div key={dept.id}>
                  <h3>{dept.name}</h3>
                  {filteredPeople
                    .filter((user) => user.departmentId === dept.id)
                    .map((user) => (
                      <PersonButton
                        key={user.id}
                        user={user}
                        active={user.id === personId}
                        count={monthAbnormal(user.id, cursor.year, cursor.month, overrides)}
                        onClick={() => setPersonId(user.id)}
                      />
                    ))}
                </div>
              ))}
          </aside>
        )}

        <div className="panel calendar-card">
          <div className="month-nav">
            <button type="button" className="btn btn-ghost btn-small" onClick={() => shiftMonth(-1)}>
              上个月
            </button>
            <strong className="num">
              {cursor.year}年{cursor.month}月
            </strong>
            <button type="button" className="btn btn-ghost btn-small" onClick={() => shiftMonth(1)}>
              下个月
            </button>
          </div>
          <p className="fine">{person ? `${person.name} · ${person.employeeNo}` : '没有可查看的人员'}</p>
          <div className="legend">
            <button type="button" className={focus === 'all' ? 'is-on' : ''} onClick={() => setFocus('all')}>
              全部
            </button>
            {counts.map((item) => (
              <button
                key={item.status}
                type="button"
                className={focus === item.status ? 'is-on' : ''}
                data-status={item.status}
                onClick={() => setFocus(item.status)}
              >
                {STATUS_LABEL[item.status]} <b>{item.count}</b>
              </button>
            ))}
          </div>
          <div className="calendar-grid">
            {['一', '二', '三', '四', '五', '六', '日'].map((label) => (
              <div key={label} className="dow">
                {label}
              </div>
            ))}
            {person &&
              cells.map((day, index) => {
                if (!day) return <div key={`blank-${index}`} className="day is-empty" />
                const date = ymd(cursor.year, cursor.month, day)
                const override = overrides[`${person.id}|${date}`]
                const status = override?.status ?? baseAttendance(person.id, date).status
                const dim = focus !== 'all' && status !== focus
                return (
                  <button
                    key={date}
                    type="button"
                    className={[
                      'day',
                      date === selectedDate ? 'is-selected' : '',
                      date === todayStamp() ? 'is-today' : '',
                      dim ? 'is-dim' : '',
                    ]
                      .filter(Boolean)
                      .join(' ')}
                    data-status={status}
                    onClick={() => setSelectedDate(date)}
                  >
                    <span className="day-num">{day}</span>
                    <span className="day-tag">{STATUS_SHORT[status]}</span>
                    {override && <i className="revised" title="已人工修订" />}
                  </button>
                )
              })}
          </div>
        </div>

        <aside className="panel day-panel">
          {person && selectedDate && record ? (
            <>
              <p className="kicker">{weekdayLabel(selectedDate)}</p>
              <h2>{formatChinese(selectedDate)}</h2>
              <p>
                当前 <strong>{STATUS_LABEL[record.status]}</strong>
              </p>
              <p className="fine">来源：{record.source}</p>
              {record.override && (
                <p className="fine">
                  {users.find((user) => user.id === record.override?.editedBy)?.name} 于{' '}
                  {formatDateTime(record.override.editedAt)} 修订
                  {record.override.note ? `。备注：${record.override.note}` : ''}
                </p>
              )}
              {canEdit ? (
                <div className="form-stack">
                  <div className="status-picks">
                    {STATUSES.map((status) => (
                      <button
                        key={status}
                        type="button"
                        data-status={status}
                        className={editStatus === status ? 'is-on' : ''}
                        onClick={() => setEditStatus(status)}
                      >
                        {STATUS_LABEL[status]}
                      </button>
                    ))}
                  </div>
                  <label className="field">
                    <span>备注</span>
                    <textarea value={editNote} onChange={(event) => setEditNote(event.target.value)} rows={3} />
                  </label>
                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={() => {
                      store.setOverride(person.id, selectedDate, editStatus, editNote)
                      notify('已修订这一天')
                    }}
                  >
                    保存修订
                  </button>
                  {record.override && (
                    <button
                      type="button"
                      className="btn btn-ghost"
                      onClick={() => {
                        store.clearOverride(person.id, selectedDate)
                        notify('已撤销修订')
                      }}
                    >
                      撤销修订
                    </button>
                  )}
                </div>
              ) : (
                <p className="fine">这个身份只能查看。需要改状态时找人力管理员。</p>
              )}
            </>
          ) : (
            <p className="empty">点日历中的一天，查看是出勤、请假、出差还是市内公出。</p>
          )}
        </aside>
      </section>
    </div>
  )
}

function PersonButton({
  user,
  active,
  count,
  onClick,
}: {
  user: User
  active: boolean
  count: number
  onClick: () => void
}) {
  return (
    <button type="button" className={active ? 'person is-on' : 'person'} onClick={onClick}>
      <span>
        <strong>{user.name}</strong>
        <small>{user.employeeNo}</small>
      </span>
      <em>{count}</em>
    </button>
  )
}

function DropSlot({
  title,
  hint,
  fileName,
  active,
  onOver,
  onFile,
}: {
  title: string
  hint: string
  fileName?: string
  active: boolean
  onOver: (value: boolean) => void
  onFile: (file: File | undefined) => void
}) {
  return (
    <label
      className={active ? 'drop is-over' : 'drop'}
      onDragOver={(event) => {
        event.preventDefault()
        onOver(true)
      }}
      onDragLeave={() => onOver(false)}
      onDrop={(event) => {
        event.preventDefault()
        onOver(false)
        onFile(event.dataTransfer.files[0])
      }}
    >
      <strong>{title}</strong>
      <span>{fileName ? `已放入 ${fileName}` : hint}</span>
      <em>拖到这里，或点击选择</em>
      <input
        type="file"
        accept=".xlsx,.xls,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        onChange={(event) => {
          onFile(event.target.files?.[0])
          event.target.value = ''
        }}
      />
    </label>
  )
}

function Preview({ title, preview }: { title: string; preview: ReturnType<typeof useStore>['imports']['abnormal'] }) {
  if (!preview) {
    return (
      <article className="panel">
        <h3>{title}</h3>
        <p className="empty">还没放入。</p>
      </article>
    )
  }
  return (
    <article className="panel">
      <h3>
        {title}
        <span className="fine">
          {' '}
          {preview.fileName} / {preview.sheetName} / {preview.rowCount} 行
        </span>
      </h3>
      <div className="chip-line">
        {preview.headers.filter(Boolean).map((header) => (
          <span key={header} className="chip">
            {header}
          </span>
        ))}
      </div>
      <div className="table-wrap">
        <table className="table">
          <tbody>
            {preview.rows.map((row, index) => (
              <tr key={index}>
                {preview.headers.map((_, cell) => (
                  <td key={cell}>{row[cell] ?? ''}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </article>
  )
}
