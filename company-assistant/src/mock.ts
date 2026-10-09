import { illustration } from './lib/image'
import { shiftDay } from './lib/format'
import type { PersistedShape } from './persist'
import { INITIAL_PASSWORD, type Answer, type Department, type Questionnaire, type UnionGroup, type User } from './types'

const password = INITIAL_PASSWORD

function user(
  id: string,
  name: string,
  employeeNo: string,
  departmentId: string,
  unionGroupId: string,
  roles: User['roles'],
): User {
  return { id, name, employeeNo, departmentId, unionGroupId, roles, password, active: true }
}

function answer(
  id: string,
  questionnaireId: string,
  userId: string,
  selections: Record<string, string[]>,
  daysAgo: number,
): Answer {
  const date = new Date()
  date.setDate(date.getDate() - daysAgo)
  date.setHours(9 + (daysAgo % 6), 10 + (daysAgo % 4) * 7, 0, 0)
  return { id, questionnaireId, userId, selections, submittedAt: date.toISOString() }
}

export function createSeed(): PersistedShape {
  const departments: Department[] = [
    { id: 'd-ops', name: '运转车间' },
    { id: 'd-fix', name: '检修车间' },
    { id: 'd-pass', name: '客运车间' },
    { id: 'd-office', name: '综合办公室' },
    { id: 'd-hr', name: '人力资源部' },
  ]
  const groups: UnionGroup[] = [
    { id: 'g-ops', name: '运转第一小组' },
    { id: 'g-fix', name: '检修第一小组' },
    { id: 'g-pass', name: '客运第一小组' },
    { id: 'g-office', name: '机关小组' },
  ]
  const users: User[] = [
    user('u-zhao', '赵衡', 'E1001', 'd-office', 'g-office', ['admin', 'union_member']),
    user('u-shen', '沈清和', 'E1002', 'd-office', 'g-office', ['union_admin', 'union_member']),
    user('u-jiang', '蒋南', 'E1003', 'd-office', 'g-office', ['union_member']),
    user('u-zhou', '周晚宁', 'E2001', 'd-ops', 'g-ops', ['union_leader', 'union_member']),
    user('u-han', '韩叙', 'E2008', 'd-ops', 'g-ops', ['dept_head', 'union_member']),
    user('u-ma', '马晓东', 'E2002', 'd-ops', 'g-ops', ['union_member']),
    user('u-lin', '林夏', 'E2003', 'd-ops', 'g-ops', ['union_member']),
    user('u-wu', '吴凯', 'E2004', 'd-ops', 'g-ops', ['union_member']),
    user('u-zheng', '郑一楠', 'E2005', 'd-ops', 'g-ops', ['union_member']),
    user('u-chen', '陈予安', 'E3001', 'd-fix', 'g-fix', ['union_leader', 'union_member']),
    user('u-sun', '孙启明', 'E3002', 'd-fix', 'g-fix', ['union_member']),
    user('u-he', '何婉', 'E3003', 'd-fix', 'g-fix', ['union_member']),
    user('u-luo', '罗川', 'E3004', 'd-fix', 'g-fix', ['union_member']),
    user('u-feng', '冯雪', 'E5001', 'd-pass', 'g-pass', ['union_member']),
    user('u-ding', '丁一', 'E5002', 'd-pass', 'g-pass', ['union_member']),
    user('u-xu', '许南枝', 'E4001', 'd-hr', 'g-office', ['hr_admin', 'union_member']),
  ]

  const standard = illustration('标准礼盒', '#8c3b2a')
  const light = illustration('轻量礼盒', '#1d4e89')
  const plain = illustration('无糖礼盒', '#1f6b45')

  const questionnaires: Questionnaire[] = [
    {
      id: 'q-mid',
      title: '中秋食品领取',
      description: '本次工会发放月饼礼盒。请按自己的情况选择规格，小组长会按填写结果生成签收单。',
      itemName: '中秋食品礼包',
      deadline: shiftDay(9),
      status: 'published',
      publishedAt: new Date(Date.now() - 5 * 86400000).toISOString(),
      createdBy: 'u-shen',
      questions: [
        {
          id: 'q-mid-box',
          type: 'single',
          title: '选择礼盒规格',
          required: true,
          options: [
            { id: 'box-std', label: '标准礼盒（月饼 8 枚 + 茶叶）', image: standard },
            { id: 'box-light', label: '轻量礼盒（月饼 4 枚）', image: light },
            { id: 'box-plain', label: '无糖礼盒', image: plain },
          ],
        },
        {
          id: 'q-mid-snack',
          type: 'multiple',
          title: '加选随包小食',
          required: false,
          options: [
            { id: 'snack-nut', label: '坚果' },
            { id: 'snack-candy', label: '蜜饯' },
            { id: 'snack-none', label: '不用加选' },
          ],
        },
      ],
    },
    {
      id: 'q-winter',
      title: '冬季劳保用品尺码',
      description: '请填写工装和棉鞋尺码，便于小组统一领取。',
      itemName: '冬季劳保',
      deadline: shiftDay(18),
      status: 'published',
      publishedAt: new Date(Date.now() - 2 * 86400000).toISOString(),
      createdBy: 'u-shen',
      questions: [
        {
          id: 'q-winter-cloth',
          type: 'single',
          title: '工装尺码',
          required: true,
          options: ['S', 'M', 'L', 'XL', 'XXL'].map((label) => ({ id: `cloth-${label}`, label })),
        },
        {
          id: 'q-winter-shoe',
          type: 'single',
          title: '棉鞋尺码',
          required: true,
          options: ['38', '39', '40', '41', '42', '43', '44'].map((label) => ({ id: `shoe-${label}`, label })),
        },
      ],
    },
    {
      id: 'q-spring',
      title: '春节慰问品意向',
      description: '还在起草，尚未推送给成员。',
      itemName: '春节慰问品',
      deadline: shiftDay(70),
      status: 'draft',
      createdBy: 'u-shen',
      questions: [
        {
          id: 'q-spring-kind',
          type: 'single',
          title: '更希望哪一类',
          required: true,
          options: [
            { id: 'sp-oil', label: '粮油' },
            { id: 'sp-fruit', label: '水果' },
            { id: 'sp-daily', label: '日用品' },
          ],
        },
      ],
    },
  ]

  const mid = (userId: string, box: string, snacks: string[], daysAgo: number) =>
    answer(`a-mid-${userId}`, 'q-mid', userId, { 'q-mid-box': [box], 'q-mid-snack': snacks }, daysAgo)
  const winter = (userId: string, cloth: string, shoe: string, daysAgo: number) =>
    answer(
      `a-winter-${userId}`,
      'q-winter',
      userId,
      { 'q-winter-cloth': [`cloth-${cloth}`], 'q-winter-shoe': [`shoe-${shoe}`] },
      daysAgo,
    )

  const answers: Answer[] = [
    mid('u-shen', 'box-std', ['snack-nut'], 4),
    mid('u-jiang', 'box-plain', ['snack-none'], 3),
    mid('u-zhou', 'box-std', ['snack-nut', 'snack-candy'], 4),
    mid('u-han', 'box-light', ['snack-nut'], 3),
    mid('u-ma', 'box-std', ['snack-candy'], 2),
    mid('u-lin', 'box-plain', ['snack-none'], 2),
    mid('u-wu', 'box-light', ['snack-nut'], 1),
    mid('u-zheng', 'box-std', [], 1),
    mid('u-chen', 'box-std', ['snack-nut'], 3),
    mid('u-sun', 'box-light', ['snack-none'], 2),
    mid('u-feng', 'box-plain', ['snack-candy'], 1),
    winter('u-shen', 'M', '39', 1),
    winter('u-zhou', 'L', '42', 1),
    winter('u-han', 'XL', '43', 1),
    winter('u-ma', 'L', '41', 1),
    winter('u-lin', 'S', '38', 1),
    winter('u-chen', 'L', '42', 1),
    winter('u-sun', 'M', '40', 1),
    winter('u-he', 'S', '38', 1),
    winter('u-luo', 'XL', '44', 1),
    winter('u-ding', 'M', '40', 1),
  ]

  return {
    version: 1,
    users,
    departments,
    groups,
    questionnaires,
    answers,
    overrides: {},
    imports: { abnormal: null, supplement: null, importedAt: null },
    invoices: [],
    sessionUserId: null,
  }
}
