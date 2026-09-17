import assert from 'node:assert/strict'
import { test } from 'node:test'
import { ApiError } from '../src/api/error.ts'
import { axisPercent, barSpan, comparisonAxis, sparkPaths } from '../src/contest/charts.ts'
import {
  describeDisqualification,
  formatPercent,
  formatRemaining,
  formatScore,
  progressRatio,
} from '../src/contest/labels.ts'
import { describeSubmitError, isEntryNotFound, isResultNotPublished } from '../src/contest/submitErrorMessage.ts'
import { awaitingScore, parseSourceWeeks } from '../src/contest/types.ts'

const apiError = (status, code) =>
  new ApiError(status, { timestamp: '', status, code, message: '서버 문구', path: '/contests', validationErrors: [] })

test('실격 사유는 걸린 장세와 함께 읽는다', () => {
  assert.equal(describeDisqualification('MDD_ABOVE_LIMIT', 'BEAR'), '하락장 최대 낙폭 실격선 초과')
  assert.equal(describeDisqualification('LOW_EXPOSURE', null), '평균 투자 비중 미달')
  assert.equal(describeDisqualification(null, 'BULL'), null)
})

test('값이 없는 지표는 비워 두고 추정하지 않는다', () => {
  assert.equal(formatPercent(0.2389), '23.9%')
  assert.equal(formatPercent(-0.0488), '-4.9%')
  assert.equal(formatPercent(null), '—')
  assert.equal(formatScore(73.333), '73.3')
  assert.equal(formatScore(null), '—')
})

test('남은 시간은 분 아래를 버리고 마감이 지나면 없다', () => {
  const now = Date.parse('2026-09-16T00:00:00Z')
  assert.equal(formatRemaining('2026-09-18T14:22:00Z', now), '2일 14시간')
  assert.equal(formatRemaining('2026-09-16T03:12:40Z', now), '3시간 12분')
  assert.equal(formatRemaining('2026-09-16T00:40:00Z', now), '40분')
  assert.equal(formatRemaining('2026-09-15T23:59:00Z', now), null)
  assert.equal(formatRemaining('마감 없음', now), null)
})

test('진행률은 0과 1 사이로 자르고 백테스트가 없으면 0이다', () => {
  assert.equal(progressRatio(2, 4), 0.5)
  assert.equal(progressRatio(0, 0), 0)
  assert.equal(progressRatio(5, 4), 1)
})

test('제출 실패는 코드마다 다음 행동이 다르다', () => {
  const closed = describeSubmitError(apiError(409, 'CONTEST-409-001'))
  assert.equal(closed.retryable, false)
  assert.match(closed.title, /제출 기간/)

  const limit = describeSubmitError(apiError(409, 'CONTEST-409-002'))
  assert.equal(limit.retryable, false)
  assert.match(limit.title, /횟수/)

  const factor = describeSubmitError(apiError(400, 'CONTEST-400-001'))
  assert.equal(factor.retryable, false)
  assert.match(factor.title, /팩터 기간/)

  const strategy = describeSubmitError(apiError(404, 'STRATEGY-404-001'))
  assert.equal(strategy.retryable, true)

  const unknown = describeSubmitError(new Error('network down'))
  assert.equal(unknown.retryable, true)
  assert.match(unknown.description, /서버에 연결할 수 없습니다/)
})

test('결과 미공개와 미제출은 오류가 아니라 상태다', () => {
  assert.equal(isResultNotPublished(apiError(409, 'CONTEST-409-003')), true)
  assert.equal(isResultNotPublished(apiError(409, 'CONTEST-409-001')), false)
  assert.equal(isEntryNotFound(apiError(404, 'CONTEST-404-002')), true)
  assert.equal(isEntryNotFound(apiError(404, 'CONTEST-404-001')), false)
})

test('뽑은 주 목록이 깨져 있어도 화면은 넘어간다', () => {
  const weeks = parseSourceWeeks('[{"source_start":"2024-01-01T00:00:00Z","label":"w1"}]')
  assert.deepEqual(weeks, [{ sourceStart: '2024-01-01T00:00:00Z', label: 'w1' }])
  assert.deepEqual(parseSourceWeeks('not json'), [])
  assert.deepEqual(parseSourceWeeks('{}'), [])
  assert.deepEqual(parseSourceWeeks('[1,2]'), [])
})

test('자산 곡선은 수익률 0에서 출발하고 가장 높은 값이 위, 낮은 값이 아래에 온다', () => {
  const paths = sparkPaths([0.1, -0.1])

  // 0 → 0.1 → -0.1: x 는 4·100·196, y 는 가운데·위(4)·아래(42)
  assert.equal(paths.line, 'M4.0,23.0L100.0,4.0L196.0,42.0')
  assert.equal(paths.area, 'M4.0,23.0L100.0,4.0L196.0,42.0L196,46L4,46Z')
})

test('자산 곡선이 비면 그리지 않고, 값이 모두 0이면 가운데 평평한 선이다', () => {
  assert.equal(sparkPaths([]), null)
  assert.equal(sparkPaths([0]).line, 'M4.0,23.0L196.0,23.0')
})

test('비교 축은 0을 포함하고 음수 막대는 0의 왼쪽으로 뻗는다', () => {
  const axis = comparisonAxis([2.3, -0.2, null, 1.28])

  assert.deepEqual(axis, { low: -0.2, high: 2.3 })
  assert.equal(axisPercent(1.28, axis).toFixed(1), '59.2')
  const negative = barSpan(-0.2, axis)
  assert.equal(negative.left, 0)
  assert.equal(negative.width.toFixed(1), '8.0')
  const positive = barSpan(2.3, axis)
  assert.equal(positive.left.toFixed(1), '8.0')
  assert.equal(positive.width.toFixed(1), '92.0')
})

test('값이 모두 0이거나 없으면 축 폭을 1로 둬 나눗셈이 깨지지 않는다', () => {
  assert.deepEqual(comparisonAxis([0, null]), { low: 0, high: 1 })
  assert.deepEqual(barSpan(0, comparisonAxis([])), { left: 0, width: 0 })
})

test('백테스트가 다 끝나도 점수와 실격 사유가 비어 있으면 아직 채점을 기다린다', () => {
  assert.equal(awaitingScore({ totalScore: null, disqualificationReason: null }), true)
  assert.equal(awaitingScore({ totalScore: 72.1, disqualificationReason: null }), false)
  assert.equal(awaitingScore({ totalScore: null, disqualificationReason: 'MDD_ABOVE_LIMIT' }), false)
})
