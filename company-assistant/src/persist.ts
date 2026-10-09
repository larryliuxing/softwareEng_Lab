import type {
  Answer,
  AttendanceOverride,
  Department,
  InvoiceFile,
  Questionnaire,
  TablePreview,
  UnionGroup,
  User,
} from './types'

export type PersistedShape = {
  version: 1
  users: User[]
  departments: Department[]
  groups: UnionGroup[]
  questionnaires: Questionnaire[]
  answers: Answer[]
  overrides: Record<string, AttendanceOverride>
  imports: {
    abnormal: TablePreview | null
    supplement: TablePreview | null
    importedAt: string | null
  }
  invoices: InvoiceFile[]
  sessionUserId: string | null
}

export const STORAGE_KEY = 'company-assistant-state-v1'
