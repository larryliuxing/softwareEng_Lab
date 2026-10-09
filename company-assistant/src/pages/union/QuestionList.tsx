import type { Questionnaire } from '../../types'

type QuestionListProps = {
  questionnaire: Questionnaire
  selections: Record<string, string[]>
  disabled?: boolean
  onToggle?: (questionId: string, optionId: string) => void
}

export function QuestionList({ questionnaire, selections, disabled, onToggle }: QuestionListProps) {
  return (
    <div className="question-list">
      {questionnaire.questions.map((question, index) => (
        <fieldset key={question.id} className="q-block" disabled={disabled}>
          <legend>
            {index + 1}. {question.title || '未命题'}
            <em>{question.type === 'multiple' ? '多选' : '单选'}</em>
            {!question.required && <em>选填</em>}
          </legend>
          {question.image && <img className="q-image" src={question.image} alt="" />}
          <div className={question.options.some((option) => option.image) ? 'choice-grid' : 'choice-stack'}>
            {question.options.map((option) => {
              const checked = (selections[question.id] ?? []).includes(option.id)
              return (
                <label key={option.id} className={checked ? 'choice is-on' : 'choice'}>
                  <input
                    type={question.type === 'single' ? 'radio' : 'checkbox'}
                    name={question.id}
                    checked={checked}
                    onChange={() => onToggle?.(question.id, option.id)}
                  />
                  {option.image && <img src={option.image} alt="" />}
                  <span>{option.label}</span>
                </label>
              )
            })}
          </div>
        </fieldset>
      ))}
    </div>
  )
}
