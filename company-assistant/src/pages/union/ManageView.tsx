import { useState } from 'react'
import { readImageFile } from '../../lib/image'
import { formatChinese, uid } from '../../lib/format'
import { useStore } from '../../store'
import type { Questionnaire, Question, QuestionOption } from '../../types'
import { QuestionList } from './QuestionList'

function blankOption(): QuestionOption {
  return { id: uid('opt'), label: '' }
}

function blankQuestion(): Question {
  return { id: uid('qq'), type: 'single', title: '', required: true, options: [blankOption(), blankOption()] }
}

function blankQuestionnaire(userId: string): Questionnaire {
  return {
    id: uid('q'),
    title: '',
    description: '',
    itemName: '',
    deadline: '',
    status: 'draft',
    createdBy: userId,
    questions: [blankQuestion()],
  }
}

function validate(questionnaire: Questionnaire): string | null {
  if (!questionnaire.title.trim()) return '请填写问卷标题'
  if (!questionnaire.itemName.trim()) return '请填写发放物品，签收单要用'
  if (!questionnaire.deadline) return '请设置截止日期'
  if (questionnaire.questions.length === 0) return '至少加一道题'
  for (const [index, question] of questionnaire.questions.entries()) {
    if (!question.title.trim()) return `第 ${index + 1} 题还没有题干`
    if (question.options.filter((option) => option.label.trim()).length < 2) {
      return `第 ${index + 1} 题至少要两个选项`
    }
  }
  return null
}

function tidy(questionnaire: Questionnaire, dropEmpty: boolean): Questionnaire {
  return {
    ...questionnaire,
    title: questionnaire.title.trim(),
    description: questionnaire.description.trim(),
    itemName: questionnaire.itemName.trim(),
    questions: questionnaire.questions.map((question) => ({
      ...question,
      title: question.title.trim(),
      options: (dropEmpty ? question.options.filter((option) => option.label.trim()) : question.options).map(
        (option) => ({ ...option, label: option.label.trim() }),
      ),
    })),
  }
}

const statusText = { draft: '草稿', published: '已推送', closed: '已结束' }

export function ManageView() {
  const store = useStore()
  const { session, questionnaires, saveQuestionnaire, deleteQuestionnaire, notify } = store
  const [currentId, setCurrentId] = useState(questionnaires[0]?.id ?? '')
  const [draft, setDraft] = useState<Questionnaire | null>(questionnaires[0] ?? null)
  const [dirty, setDirty] = useState(false)
  const [ask, setAsk] = useState<'publish' | 'close' | 'delete' | null>(null)

  if (!session) return null

  function select(id: string) {
    if (dirty && !window.confirm('这份问卷还有没保存的修改，离开吗？')) return
    const next = questionnaires.find((item) => item.id === id) ?? null
    setCurrentId(id)
    setDraft(next)
    setDirty(false)
    setAsk(null)
  }

  function patch(next: Questionnaire) {
    setDraft(next)
    setDirty(true)
  }

  async function putImage(file: File | undefined, apply: (image: string) => void) {
    if (!file) return
    try {
      apply(await readImageFile(file))
    } catch (error) {
      notify(error instanceof Error ? error.message : '图片没有放上去')
    }
  }

  const saved = questionnaires.find((item) => item.id === currentId)
  const editing = draft && draft.status === 'draft'

  return (
    <div className="split">
      <div className="list-pane">
        <button
          type="button"
          className="btn btn-primary btn-block"
          onClick={() => {
            if (dirty && !window.confirm('当前修改还没保存，放弃并新建吗？')) return
            const created = blankQuestionnaire(session.id)
            setCurrentId(created.id)
            setDraft(created)
            setDirty(true)
            setAsk(null)
          }}
        >
          新建问卷
        </button>
        {questionnaires.map((item) => (
          <button
            key={item.id}
            type="button"
            className={item.id === currentId ? 'q-card is-on' : 'q-card'}
            onClick={() => select(item.id)}
          >
            <strong>{item.title || '未命名问卷'}</strong>
            <span className={`badge badge-${item.status}`}>{statusText[item.status]}</span>
            <small>{item.deadline ? `截止 ${formatChinese(item.deadline)}` : '未设截止'}</small>
          </button>
        ))}
      </div>

      {draft && (
        <div className="detail-pane">
          {editing ? (
            <div className="editor-layout">
              <div className="panel form-stack">
                <label className="field">
                  <span>标题</span>
                  <input value={draft.title} onChange={(event) => patch({ ...draft, title: event.target.value })} />
                </label>
                <label className="field">
                  <span>说明</span>
                  <textarea
                    rows={3}
                    value={draft.description}
                    onChange={(event) => patch({ ...draft, description: event.target.value })}
                  />
                </label>
                <div className="two">
                  <label className="field">
                    <span>发放物品</span>
                    <input
                      value={draft.itemName}
                      placeholder="例如：中秋食品礼包"
                      onChange={(event) => patch({ ...draft, itemName: event.target.value })}
                    />
                  </label>
                  <label className="field">
                    <span>截止日期</span>
                    <input
                      type="date"
                      value={draft.deadline}
                      onChange={(event) => patch({ ...draft, deadline: event.target.value })}
                    />
                  </label>
                </div>
                {draft.questions.map((question, questionIndex) => (
                  <article key={question.id} className="q-editor">
                    <div className="q-editor-head">
                      <strong>第 {questionIndex + 1} 题</strong>
                      <div className="seg">
                        <button
                          type="button"
                          className={question.type === 'single' ? 'is-on' : ''}
                          onClick={() => updateQuestion(questionIndex, { type: 'single' })}
                        >
                          单选
                        </button>
                        <button
                          type="button"
                          className={question.type === 'multiple' ? 'is-on' : ''}
                          onClick={() => updateQuestion(questionIndex, { type: 'multiple' })}
                        >
                          多选
                        </button>
                      </div>
                      <label className="check-line">
                        <input
                          type="checkbox"
                          checked={question.required}
                          onChange={(event) => updateQuestion(questionIndex, { required: event.target.checked })}
                        />
                        必填
                      </label>
                    </div>
                    <input
                      value={question.title}
                      placeholder="题干"
                      onChange={(event) => updateQuestion(questionIndex, { title: event.target.value })}
                    />
                    <div className="image-line">
                      <label className="btn btn-ghost btn-small">
                        题目配图
                        <input
                          hidden
                          type="file"
                          accept="image/*"
                          onChange={(event) => {
                            const file = event.target.files?.[0]
                            void putImage(file, (image) => updateQuestion(questionIndex, { image }))
                            event.target.value = ''
                          }}
                        />
                      </label>
                      {question.image && (
                        <>
                          <img className="thumb" src={question.image} alt="" />
                          <button type="button" className="text-btn" onClick={() => updateQuestion(questionIndex, { image: undefined })}>
                            移除
                          </button>
                        </>
                      )}
                    </div>
                    {question.options.map((option, optionIndex) => (
                      <div key={option.id} className="option-edit">
                        <input
                          value={option.label}
                          placeholder={`选项 ${optionIndex + 1}`}
                          onChange={(event) => updateOption(questionIndex, optionIndex, { label: event.target.value })}
                        />
                        <label className="btn btn-ghost btn-small">
                          图片
                          <input
                            hidden
                            type="file"
                            accept="image/*"
                            onChange={(event) => {
                              const file = event.target.files?.[0]
                              void putImage(file, (image) => updateOption(questionIndex, optionIndex, { image }))
                              event.target.value = ''
                            }}
                          />
                        </label>
                        {option.image && <img className="thumb" src={option.image} alt="" />}
                        {option.image && (
                          <button
                            type="button"
                            className="text-btn"
                            onClick={() => updateOption(questionIndex, optionIndex, { image: undefined })}
                          >
                            移除
                          </button>
                        )}
                        <button type="button" className="text-btn" onClick={() => removeOption(questionIndex, optionIndex)}>
                          删除
                        </button>
                      </div>
                    ))}
                    <div className="row-actions">
                      <button type="button" className="btn btn-ghost btn-small" onClick={() => addOption(questionIndex)}>
                        添加选项
                      </button>
                      <button type="button" className="btn btn-ghost btn-small" onClick={() => moveQuestion(questionIndex, -1)}>
                        上移
                      </button>
                      <button type="button" className="btn btn-ghost btn-small" onClick={() => moveQuestion(questionIndex, 1)}>
                        下移
                      </button>
                      <button type="button" className="btn btn-ghost btn-small" onClick={() => removeQuestion(questionIndex)}>
                        删除此题
                      </button>
                    </div>
                  </article>
                ))}
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={() => patch({ ...draft, questions: [...draft.questions, blankQuestion()] })}
                >
                  添加题目
                </button>
                <div className="row-actions">
                  <button
                    type="button"
                    className="btn btn-ghost"
                    onClick={() => {
                      const next = tidy(draft, false)
                      saveQuestionnaire(next)
                      setDraft(next)
                      setCurrentId(next.id)
                      setDirty(false)
                      notify('草稿已保存')
                    }}
                  >
                    保存草稿
                  </button>
                  <button type="button" className="btn btn-primary" onClick={() => setAsk('publish')}>
                    推送给全体工会成员
                  </button>
                  {saved?.status === 'draft' && (
                    <button type="button" className="btn btn-danger" onClick={() => setAsk('delete')}>
                      删除草稿
                    </button>
                  )}
                </div>
                {ask === 'publish' && (
                  <div className="confirm-line">
                    <p>推送后，全体工会成员都会在「待我填写」里看到。确定推送？</p>
                    <button
                      type="button"
                      className="btn btn-primary btn-small"
                      onClick={() => {
                        const next = tidy(draft, true)
                        const message = validate(next)
                        if (message) {
                          notify(message)
                          return
                        }
                        const published: Questionnaire = {
                          ...next,
                          status: 'published',
                          publishedAt: new Date().toISOString(),
                        }
                        saveQuestionnaire(published)
                        setDraft(published)
                        setCurrentId(published.id)
                        setDirty(false)
                        setAsk(null)
                        notify('已推送给全体工会成员')
                      }}
                    >
                      确定推送
                    </button>
                    <button type="button" className="btn btn-ghost btn-small" onClick={() => setAsk(null)}>
                      取消
                    </button>
                  </div>
                )}
                {ask === 'delete' && saved && (
                  <div className="confirm-line">
                    <p>删除后不能恢复。</p>
                    <button
                      type="button"
                      className="btn btn-danger btn-small"
                      onClick={() => {
                        const message = deleteQuestionnaire(saved.id)
                        if (message) {
                          notify(message)
                          return
                        }
                        const rest = questionnaires.filter((item) => item.id !== saved.id)
                        setCurrentId(rest[0]?.id ?? '')
                        setDraft(rest[0] ?? null)
                        setDirty(false)
                        setAsk(null)
                        notify('草稿已删除')
                      }}
                    >
                      确定删除
                    </button>
                    <button type="button" className="btn btn-ghost btn-small" onClick={() => setAsk(null)}>
                      取消
                    </button>
                  </div>
                )}
              </div>
              <aside className="panel preview-card">
                <p className="kicker">预览</p>
                <h2>{draft.title || '未命名问卷'}</h2>
                <p className="lede">{draft.description || '说明会显示在这里。'}</p>
                <QuestionList questionnaire={draft} selections={{}} disabled />
              </aside>
            </div>
          ) : (
            <div className="panel">
              <header className="page-head">
                <div>
                  <p className="kicker">{statusText[draft.status]}</p>
                  <h2>{draft.title}</h2>
                  <p className="lede">{draft.description}</p>
                  <p className="fine">
                    发放物品：{draft.itemName} · 截止 {draft.deadline ? formatChinese(draft.deadline) : '未设'}
                  </p>
                </div>
              </header>
              <QuestionList questionnaire={draft} selections={{}} disabled />
              {draft.status === 'published' && (
                <div className="confirm-line">
                  {ask === 'close' ? (
                    <>
                      <p>结束后不能再填写。已经提交的答案还在，小组长仍可生成签收单。</p>
                      <button
                        type="button"
                        className="btn btn-danger btn-small"
                        onClick={() => {
                          const closed: Questionnaire = { ...draft, status: 'closed', closedAt: new Date().toISOString() }
                          saveQuestionnaire(closed)
                          setDraft(closed)
                          setAsk(null)
                          notify('问卷已结束')
                        }}
                      >
                        确定结束
                      </button>
                      <button type="button" className="btn btn-ghost btn-small" onClick={() => setAsk(null)}>
                        取消
                      </button>
                    </>
                  ) : (
                    <button type="button" className="btn btn-danger" onClick={() => setAsk('close')}>
                      结束问卷
                    </button>
                  )}
                </div>
              )}
              <p className="fine">已推送的题目不再改，避免和已经填过的人对不上。要调整就新建一份。</p>
            </div>
          )}
        </div>
      )}
    </div>
  )

  function updateQuestion(index: number, patchQuestion: Partial<Question>) {
    if (!draft) return
    const questions = draft.questions.map((question, questionIndex) =>
      questionIndex === index ? { ...question, ...patchQuestion } : question,
    )
    patch({ ...draft, questions })
  }

  function updateOption(questionIndex: number, optionIndex: number, patchOption: Partial<QuestionOption>) {
    if (!draft) return
    const questions = draft.questions.map((question, index) => {
      if (index !== questionIndex) return question
      return {
        ...question,
        options: question.options.map((option, current) => (current === optionIndex ? { ...option, ...patchOption } : option)),
      }
    })
    patch({ ...draft, questions })
  }

  function addOption(questionIndex: number) {
    if (!draft) return
    const question = draft.questions[questionIndex]
    if (question.options.length >= 12) {
      notify('一道题最多 12 个选项')
      return
    }
    updateQuestion(questionIndex, { options: [...question.options, blankOption()] })
  }

  function removeOption(questionIndex: number, optionIndex: number) {
    if (!draft) return
    const question = draft.questions[questionIndex]
    updateQuestion(questionIndex, { options: question.options.filter((_, index) => index !== optionIndex) })
  }

  function removeQuestion(index: number) {
    if (!draft) return
    patch({ ...draft, questions: draft.questions.filter((_, questionIndex) => questionIndex !== index) })
  }

  function moveQuestion(index: number, delta: number) {
    if (!draft) return
    const target = index + delta
    if (target < 0 || target >= draft.questions.length) return
    const questions = [...draft.questions]
    const [item] = questions.splice(index, 1)
    questions.splice(target, 0, item)
    patch({ ...draft, questions })
  }
}
