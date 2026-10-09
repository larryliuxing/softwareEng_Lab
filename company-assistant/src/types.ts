export type Role =
  | 'admin'
  | 'union_admin'
  | 'union_leader'
  | 'union_member'
  | 'hr_admin'
  | 'dept_head'

export const ROLE_ORDER: Role[] = [
  'admin',
  'hr_admin',
  'dept_head',
  'union_admin',
  'union_leader',
  'union_member',
]

export const ROLE_LABEL: Record<Role, string> = {
  admin: '系统管理员',
  union_admin: '工会管理',
  union_leader: '工会小组长',
  union_member: '工会成员',
  hr_admin: '人力管理员',
  dept_head: '部门负责人',
}

export type User = {
  id: string
  name: string
  employeeNo: string
  departmentId: string
  unionGroupId: string
  roles: Role[]
  password: string
  active: boolean
}

export type Department = { id: string; name: string }
export type UnionGroup = { id: string; name: string }

export type ChoiceType = 'single' | 'multiple'

export type QuestionOption = {
  id: string
  label: string
  image?: string
}

export type Question = {
  id: string
  type: ChoiceType
  title: string
  required: boolean
  image?: string
  options: QuestionOption[]
}

export type Questionnaire = {
  id: string
  title: string
  description: string
  itemName: string
  deadline: string
  status: 'draft' | 'published' | 'closed'
  questions: Question[]
  createdBy: string
  publishedAt?: string
  closedAt?: string
}

export type Answer = {
  id: string
  questionnaireId: string
  userId: string
  submittedAt: string
  selections: Record<string, string[]>
}

export type DayStatus = 'work' | 'leave' | 'trip' | 'local' | 'rest'

export const STATUS_LABEL: Record<DayStatus, string> = {
  work: '出勤',
  leave: '请假',
  trip: '出差',
  local: '市内公出',
  rest: '休息',
}

export const STATUS_SHORT: Record<DayStatus, string> = {
  work: '出勤',
  leave: '请假',
  trip: '出差',
  local: '公出',
  rest: '休息',
}

export type AttendanceOverride = {
  userId: string
  date: string
  status: DayStatus
  note: string
  editedBy: string
  editedAt: string
}

export type TablePreview = {
  fileName: string
  sheetName: string
  headers: string[]
  rows: string[][]
  rowCount: number
}

export type InvoiceFile = {
  id: string
  name: string
  size: number
  uploadedAt: string
}

export const INITIAL_PASSWORD = '888888'

export function sortRoles(roles: Role[]): Role[] {
  return ROLE_ORDER.filter((role) => roles.includes(role))
}

export function hasRole(user: User | null | undefined, role: Role): boolean {
  return !!user?.roles.includes(role)
}
