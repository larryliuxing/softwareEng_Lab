import { createContext, useContext, useEffect, useReducer, useState, type ReactNode } from 'react'
import { createSeed } from './mock'
import { STORAGE_KEY, type PersistedShape } from './persist'
import {
  INITIAL_PASSWORD,
  sortRoles,
  type Answer,
  type AttendanceOverride,
  type DayStatus,
  type Department,
  type InvoiceFile,
  type Questionnaire,
  type Role,
  type TablePreview,
  type UnionGroup,
  type User,
} from './types'
import { uid } from './lib/format'

type Action =
  | { type: 'replace'; state: PersistedShape }
  | { type: 'login'; userId: string }
  | { type: 'logout' }
  | { type: 'changePassword'; userId: string; password: string }
  | { type: 'upsertUser'; user: User }
  | { type: 'resetPassword'; userId: string }
  | { type: 'addDepartment'; department: Department }
  | { type: 'addGroup'; group: UnionGroup }
  | { type: 'upsertQuestionnaire'; questionnaire: Questionnaire }
  | { type: 'deleteQuestionnaire'; id: string }
  | { type: 'submitAnswer'; answer: Answer }
  | { type: 'setOverride'; override: AttendanceOverride }
  | { type: 'clearOverride'; key: string }
  | { type: 'setImport'; slot: 'abnormal' | 'supplement'; preview: TablePreview }
  | { type: 'clearImports' }
  | { type: 'addInvoices'; files: InvoiceFile[] }
  | { type: 'removeInvoice'; id: string }

export type UserDraft = {
  id?: string
  name: string
  employeeNo: string
  departmentId: string
  unionGroupId: string
  roles: Role[]
  active: boolean
}

function reducer(state: PersistedShape, action: Action): PersistedShape {
  switch (action.type) {
    case 'replace':
      return action.state
    case 'login':
      return { ...state, sessionUserId: action.userId }
    case 'logout':
      return { ...state, sessionUserId: null }
    case 'changePassword':
      return {
        ...state,
        users: state.users.map((user) =>
          user.id === action.userId ? { ...user, password: action.password } : user,
        ),
      }
    case 'upsertUser': {
      const exists = state.users.some((user) => user.id === action.user.id)
      return {
        ...state,
        users: exists
          ? state.users.map((user) => (user.id === action.user.id ? action.user : user))
          : [...state.users, action.user],
      }
    }
    case 'resetPassword':
      return {
        ...state,
        users: state.users.map((user) =>
          user.id === action.userId ? { ...user, password: INITIAL_PASSWORD } : user,
        ),
      }
    case 'addDepartment':
      return { ...state, departments: [...state.departments, action.department] }
    case 'addGroup':
      return { ...state, groups: [...state.groups, action.group] }
    case 'upsertQuestionnaire': {
      const exists = state.questionnaires.some((item) => item.id === action.questionnaire.id)
      return {
        ...state,
        questionnaires: exists
          ? state.questionnaires.map((item) =>
              item.id === action.questionnaire.id ? action.questionnaire : item,
            )
          : [action.questionnaire, ...state.questionnaires],
      }
    }
    case 'deleteQuestionnaire':
      return {
        ...state,
        questionnaires: state.questionnaires.filter((item) => item.id !== action.id),
        answers: state.answers.filter((item) => item.questionnaireId !== action.id),
      }
    case 'submitAnswer':
      return {
        ...state,
        answers: [
          ...state.answers.filter(
            (item) =>
              !(item.questionnaireId === action.answer.questionnaireId && item.userId === action.answer.userId),
          ),
          action.answer,
        ],
      }
    case 'setOverride':
      return {
        ...state,
        overrides: { ...state.overrides, [`${action.override.userId}|${action.override.date}`]: action.override },
      }
    case 'clearOverride': {
      const overrides = { ...state.overrides }
      delete overrides[action.key]
      return { ...state, overrides }
    }
    case 'setImport':
      return {
        ...state,
        imports: { ...state.imports, [action.slot]: action.preview, importedAt: new Date().toISOString() },
      }
    case 'clearImports':
      return { ...state, imports: { abnormal: null, supplement: null, importedAt: null } }
    case 'addInvoices':
      return { ...state, invoices: [...action.files, ...state.invoices].slice(0, 20) }
    case 'removeInvoice':
      return { ...state, invoices: state.invoices.filter((file) => file.id !== action.id) }
    default:
      return state
  }
}

function loadState(): PersistedShape {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const data = JSON.parse(raw) as PersistedShape
      if (data.version === 1 && Array.isArray(data.users) && Array.isArray(data.questionnaires)) return data
    }
  } catch {
    /* 使用初始演示数据 */
  }
  return createSeed()
}

type Toast = { id: number; message: string }

type Store = PersistedShape & {
  session: User | null
  toast: Toast | null
  notify: (message: string) => void
  dismissToast: () => void
  login: (name: string, password: string) => string | null
  logout: () => void
  changePassword: (oldPassword: string, nextPassword: string) => string | null
  saveUser: (draft: UserDraft) => string | null
  resetPassword: (userId: string) => void
  addDepartment: (name: string) => string | null
  addGroup: (name: string) => string | null
  saveQuestionnaire: (questionnaire: Questionnaire) => void
  deleteQuestionnaire: (id: string) => string | null
  submitAnswer: (questionnaireId: string, selections: Record<string, string[]>) => string | null
  setOverride: (userId: string, date: string, status: DayStatus, note: string) => void
  clearOverride: (userId: string, date: string) => void
  setImport: (slot: 'abnormal' | 'supplement', preview: TablePreview) => void
  clearImports: () => void
  addInvoices: (files: File[]) => void
  removeInvoice: (id: string) => void
  resetDemo: () => void
}

const Ctx = createContext<Store | null>(null)

export function StoreProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, undefined, loadState)
  const [toast, setToast] = useState<Toast | null>(null)

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  }, [state])

  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(null), 2800)
    return () => window.clearTimeout(timer)
  }, [toast])

  const session = state.users.find((user) => user.id === state.sessionUserId) ?? null

  useEffect(() => {
    if (session && !session.active) dispatch({ type: 'logout' })
  }, [session])

  const notify = (message: string) => setToast({ id: Date.now(), message })

  const value: Store = {
    ...state,
    session,
    toast,
    notify,
    dismissToast: () => setToast(null),
    login(name, password) {
      const trimmed = name.trim()
      if (!trimmed || !password) return '请输入姓名和密码'
      const user = state.users.find((item) => item.name === trimmed)
      if (!user || user.password !== password) return '姓名或密码不正确'
      if (!user.active) return '该账号已停用，请联系管理员'
      dispatch({ type: 'login', userId: user.id })
      return null
    },
    logout() {
      dispatch({ type: 'logout' })
    },
    changePassword(oldPassword, nextPassword) {
      if (!session) return '请先登录'
      if (session.password !== oldPassword) return '原密码不正确'
      if (nextPassword.length < 6) return '新密码至少 6 位'
      if (nextPassword === oldPassword) return '新密码不能与原密码相同'
      dispatch({ type: 'changePassword', userId: session.id, password: nextPassword })
      return null
    },
    saveUser(draft) {
      const name = draft.name.trim()
      const employeeNo = draft.employeeNo.trim()
      if (!name) return '请填写姓名'
      if (!employeeNo) return '请填写工号'
      if (!draft.departmentId) return '请选择部门'
      if (!draft.unionGroupId) return '请选择工会小组'
      if (draft.roles.length === 0) return '请至少选择一个身份'
      const nameTaken = state.users.some((user) => user.name === name && user.id !== draft.id)
      if (nameTaken) return '已有同名人员，登录用的是姓名，不能重复'
      const noTaken = state.users.some((user) => user.employeeNo === employeeNo && user.id !== draft.id)
      if (noTaken) return '工号已存在'
      if (draft.id && draft.id === session?.id && !draft.roles.includes('admin') && session.roles.includes('admin')) {
        return '不能取消自己的系统管理员身份'
      }
      const previous = state.users.find((user) => user.id === draft.id)
      const next: User = {
        id: previous?.id ?? uid('u'),
        name,
        employeeNo,
        departmentId: draft.departmentId,
        unionGroupId: draft.unionGroupId,
        roles: sortRoles(draft.roles),
        password: previous?.password ?? INITIAL_PASSWORD,
        active: draft.active,
      }
      dispatch({ type: 'upsertUser', user: next })
      return null
    },
    resetPassword(userId) {
      dispatch({ type: 'resetPassword', userId })
    },
    addDepartment(name) {
      const trimmed = name.trim()
      if (!trimmed) return '请输入部门名称'
      if (state.departments.some((item) => item.name === trimmed)) return '已有同名部门'
      dispatch({ type: 'addDepartment', department: { id: uid('d'), name: trimmed } })
      return null
    },
    addGroup(name) {
      const trimmed = name.trim()
      if (!trimmed) return '请输入工会小组名称'
      if (state.groups.some((item) => item.name === trimmed)) return '已有同名小组'
      dispatch({ type: 'addGroup', group: { id: uid('g'), name: trimmed } })
      return null
    },
    saveQuestionnaire(questionnaire) {
      dispatch({ type: 'upsertQuestionnaire', questionnaire })
    },
    deleteQuestionnaire(id) {
      const questionnaire = state.questionnaires.find((item) => item.id === id)
      if (!questionnaire) return '问卷不存在'
      if (questionnaire.status !== 'draft') return '已推送的问卷不能删除，可以结束'
      dispatch({ type: 'deleteQuestionnaire', id })
      return null
    },
    submitAnswer(questionnaireId, selections) {
      if (!session) return '请先登录'
      const questionnaire = state.questionnaires.find((item) => item.id === questionnaireId)
      if (!questionnaire || questionnaire.status !== 'published') return '这份问卷现在不能填写'
      dispatch({
        type: 'submitAnswer',
        answer: {
          id: uid('a'),
          questionnaireId,
          userId: session.id,
          submittedAt: new Date().toISOString(),
          selections,
        },
      })
      return null
    },
    setOverride(userId, date, status, note) {
      if (!session) return
      dispatch({
        type: 'setOverride',
        override: {
          userId,
          date,
          status,
          note: note.trim(),
          editedBy: session.id,
          editedAt: new Date().toISOString(),
        },
      })
    },
    clearOverride(userId, date) {
      dispatch({ type: 'clearOverride', key: `${userId}|${date}` })
    },
    setImport(slot, preview) {
      dispatch({ type: 'setImport', slot, preview })
    },
    clearImports() {
      dispatch({ type: 'clearImports' })
    },
    addInvoices(files) {
      const next: InvoiceFile[] = files.map((file) => ({
        id: uid('inv'),
        name: file.name,
        size: file.size,
        uploadedAt: new Date().toISOString(),
      }))
      dispatch({ type: 'addInvoices', files: next })
    },
    removeInvoice(id) {
      dispatch({ type: 'removeInvoice', id })
    },
    resetDemo() {
      localStorage.removeItem(STORAGE_KEY)
      dispatch({ type: 'replace', state: createSeed() })
    },
  }

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useStore(): Store {
  const store = useContext(Ctx)
  if (!store) throw new Error('Store missing')
  return store
}
