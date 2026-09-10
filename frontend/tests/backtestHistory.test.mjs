import assert from 'node:assert/strict'
import { test } from 'node:test'
import { hasUnsettledRun, latestCompletedRun, runsForStrategy } from '../src/backtest/history.ts'

const run = (id, strategyId, status, completedAt, queuedAt = '2026-09-01T00:00:00Z') => ({
  id, strategyId, status,
  periodStart: new Date('2024-01-01T00:00:00Z'), periodEnd: new Date('2024-12-31T00:00:00Z'),
  timeframe: '1D', initialCapital: 3_000_000, feeRate: 0.0005, slippageBps: 5,
  queuedAt: new Date(queuedAt), startedAt: null, completedAt: completedAt === null ? null : new Date(completedAt),
})

test('전략별 이력은 식별자가 같은 실행만 남긴다', () => {
  const runs = [run('a', '1', 'COMPLETED', '2026-09-02T00:00:00Z'), run('b', '2', 'COMPLETED', '2026-09-03T00:00:00Z')]
  assert.deepEqual(runsForStrategy(runs, '1').map((item) => item.id), ['a'])
  assert.deepEqual(runsForStrategy(runs, '9'), [])
})

test('최근 완료 실행은 완료 시각이 가장 늦은 것이고 실패와 진행 중은 세지 않는다', () => {
  const runs = [
    run('old', '1', 'COMPLETED', '2026-09-01T00:00:00Z'),
    run('new', '1', 'COMPLETED', '2026-09-05T00:00:00Z'),
    run('failed', '1', 'FAILED', '2026-09-09T00:00:00Z'),
    run('running', '1', 'RUNNING', null, '2026-09-10T00:00:00Z'),
  ]
  assert.equal(latestCompletedRun(runs)?.id, 'new')
  assert.equal(latestCompletedRun([run('x', '1', 'QUEUED', null)]), null)
  assert.equal(latestCompletedRun([]), null)
})

test('완료 시각이 비어 있으면 접수 시각으로 순서를 정한다', () => {
  const runs = [run('a', '1', 'COMPLETED', null, '2026-09-01T00:00:00Z'), run('b', '1', 'COMPLETED', null, '2026-09-04T00:00:00Z')]
  assert.equal(latestCompletedRun(runs)?.id, 'b')
})

test('대기나 계산 중인 실행이 하나라도 있으면 미정산으로 본다', () => {
  assert.equal(hasUnsettledRun([run('a', '1', 'COMPLETED', '2026-09-01T00:00:00Z')]), false)
  assert.equal(hasUnsettledRun([run('a', '1', 'COMPLETED', '2026-09-01T00:00:00Z'), run('b', '1', 'QUEUED', null)]), true)
  assert.equal(hasUnsettledRun([run('b', '1', 'RUNNING', null)]), true)
})
