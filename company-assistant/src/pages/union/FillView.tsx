import { useEffect, useState } from 'react'
import { formatChinese, formatDateTime, isPastDeadline } from '../../lib/format'
import { describeAnswers } from '../../lib/excel'
import { useStore } from '../../store'
import type { Question } from '../../types'
import { QuestionList } from './QuestionList'

export function FillView() {
  const { session, questionnaires, answers, submitAnswer, notify } = useStore()
  const visible = questionnaires.filter((item) => item.status !== 'draft')
  const [currentId, setCurrentId] = useState(visible.find((item) => item.status === 'published')?.id ?? visible[0]?.id ?? '')
  const questionnaire = visible.find((item) => item.id === currentId) ?? null
  const existing = answers.find((item) => item.questionnaireId === currentId && item.userId === session?.id)
  const [selections, setSelections] = useState<Record<string, string[]>>({})

  useEffect(() => {
    setSelections(existing?.selections ?? {})
  }, [currentId, existing?.submittedAt])

  if (!session) return null
  if (visible.length === 0) return <p className="empty">还没有推送过来的问卷。</p>

  const locked = !questionnaire || questionnaire.status !== 'published' || isPastDeadline(questionnaire.deadline)

  function toggle(question: Question, optionId: string) {
    setSelections((current) => {
      const picked = current[question.id] ?? []
      if (question.type === 'single') return { ...current, [question.id]: [optionId] }
      const next = picked.includes(optionId) ? picked.filter((id) => id !== optionId) : [...picked, optionId]
      return { ...current, [question.id]: next }
    })
  }

  return (
    <div className="split">
      <div className="list-pane">
        {visible.map((item) => {
          const done = answers.some((answer) => answer.questionnaireId === item.id && answer.userId === session.id)
          return (
            <button
              key={item.id}
              type="button"
              className={item.id === currentId ? 'q-card is-on' : 'q-card'}
              onClick={() => setCurrentId(item.id)}
            >
              <strong>{item.title}</strong>
              <span>{item.itemName}</span>
              <span className={done ? 'badge badge-ok' : 'badge badge-warn'}>{done ? '已填写' : '未填写'}</span>
              <small>截止 {formatChinese(item.deadline)}</small>
            </button>
          )
        })}
      </div>
      {questionnaire && (
        <div className="detail-pane panel">
          <header className="page-head">
            <div>
              <h2>{questionnaire.title}</h2>
              <p className="lede">{questionnaire.description}</p>
              <p className="fine">
                发放物品：{questionnaire.itemName} · 截止 {formatChinese(questionnaire.deadline)}
                {questionnaire.status === 'closed' ? ' · 已结束' : ''}
                {isPastDeadline(questionnaire.deadline) ? ' · 已过截止' : ''}
              </p>
            </div>
          </header>
          <QuestionList
            questionnaire={questionnaire}
            selections={selections}
            disabled={locked}
            onToggle={(questionId, optionId) => {
              const question = questionnaire.questions.find((item) => item.id === questionId)
              if (question) toggle(question, optionId)
            }}
          />
          {existing && (
            <p className="fine">
              上次提交 {formatDateTime(existing.submittedAt)}。{describeAnswers(questionnaire.questions, existing)}
            </p>
          )}
          {!locked && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                for (const question of questionnaire.questions) {
                  if (question.required && (selections[question.id] ?? []).length === 0) {
                    notify(`请完成：${question.title}`)
                    return
                  }
                }
                const message = submitAnswer(questionnaire.id, selections)
                notify(message ?? (existing ? '已更新' : '已提交'))
              }}
            >
              {existing ? '更新提交' : '提交'}
            </button>
          )}
        </div>
      )}
    </div>
  )
}
