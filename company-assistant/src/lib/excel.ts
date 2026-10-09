import * as XLSX from 'xlsx'
import type { Answer, Department, Question, Questionnaire, TablePreview, UnionGroup, User } from '../types'

export type SignRow = {
  index: number
  name: string
  employeeNo: string
  department: string
  group: string
  content: string
  quantity: number
}

export async function readWorkbook(file: File): Promise<TablePreview> {
  const buffer = await file.arrayBuffer()
  const workbook = XLSX.read(buffer, { type: 'array' })
  const sheetName = workbook.SheetNames[0]
  if (!sheetName) throw new Error('表格里没有工作表')
  const sheet = workbook.Sheets[sheetName]
  const matrix = XLSX.utils.sheet_to_json<(string | number | null)[]>(sheet, {
    header: 1,
    raw: false,
    defval: '',
  })
  const rows = matrix.map((row) => row.map((cell) => (cell == null ? '' : String(cell))))
  const headers = rows[0] ?? []
  const body = rows.slice(1).filter((row) => row.some((cell) => cell.trim()))
  return {
    fileName: file.name,
    sheetName,
    headers,
    rows: body.slice(0, 8),
    rowCount: body.length,
  }
}

export function describeAnswers(questions: Question[], answer: Answer | undefined): string {
  if (!answer) return '未填写'
  return questions
    .map((question) => {
      const picked = answer.selections[question.id] ?? []
      const labels = question.options.filter((option) => picked.includes(option.id)).map((option) => option.label)
      return `${question.title}：${labels.join('、') || '未选'}`
    })
    .join('；')
}

export function buildSignRows(
  questionnaire: Questionnaire,
  group: UnionGroup,
  users: User[],
  answers: Answer[],
  departments: Department[],
): SignRow[] {
  const members = users
    .filter((user) => user.active && user.unionGroupId === group.id && user.roles.includes('union_member'))
    .sort((a, b) => a.employeeNo.localeCompare(b.employeeNo))
  return members.map((user, index) => {
    const answer = answers.find((item) => item.questionnaireId === questionnaire.id && item.userId === user.id)
    const department = departments.find((item) => item.id === user.departmentId)
    return {
      index: index + 1,
      name: user.name,
      employeeNo: user.employeeNo,
      department: department?.name ?? '',
      group: group.name,
      content: describeAnswers(questionnaire.questions, answer),
      quantity: 1,
    }
  })
}

export function downloadSignSheet(
  questionnaire: Questionnaire,
  group: UnionGroup,
  rows: SignRow[],
  leaderName: string,
): void {
  const header = ['序号', '姓名', '工号', '部门', '工会小组', '签收内容', '数量', '签字', '日期']
  const aoa: (string | number)[][] = [
    ['工会物品签收单'],
    [`事项：${questionnaire.title}`, '', '', `物品：${questionnaire.itemName}`, '', '', `小组：${group.name}`, ''],
    ['临时版式：正式列、盖章和页眉以后续提供的样表为准。打印后由本人在签字栏手签。'],
    [],
    header,
    ...rows.map((row) => [
      row.index,
      row.name,
      row.employeeNo,
      row.department,
      row.group,
      row.content,
      row.quantity,
      '',
      '',
    ]),
    [],
    [`小组长：${leaderName}`, '', '小组长签字：', '', '日期：', '', '工会经办：', '', ''],
  ]
  const sheet = XLSX.utils.aoa_to_sheet(aoa)
  sheet['!merges'] = [
    { s: { r: 0, c: 0 }, e: { r: 0, c: 8 } },
    { s: { r: 2, c: 0 }, e: { r: 2, c: 8 } },
  ]
  sheet['!cols'] = [6, 12, 12, 14, 16, 42, 8, 14, 14].map((width) => ({ wch: width }))
  const book = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(book, sheet, '签收单')
  const safeTitle = questionnaire.title.replace(/[\\/:*?"<>|]/g, '')
  XLSX.writeFile(book, `签收单-${group.name}-${safeTitle}.xlsx`)
}
