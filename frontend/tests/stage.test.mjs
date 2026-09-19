import assert from 'node:assert/strict'
import { test } from 'node:test'
import { primaryAction, simulationsForStrategy, strategyStage } from '../src/strategy/stage.ts'

const run = (status) => ({ id: `bt-${status}`, strategyId: 's1', status })
const sim = (id, status, startedAt) => ({ id, strategyId: 's1', status, startedAt: startedAt ? new Date(startedAt) : null, stoppedAt: null })

test('단계는 운용 중 > 대회 제출 > 검증됨 > 초안 순으로 판정한다', () => {
  assert.equal(strategyStage({ runs: [], simulations: [], submitted: false }), 'draft')
  assert.equal(strategyStage({ runs: [run('FAILED')], simulations: [], submitted: false }), 'draft')
  assert.equal(strategyStage({ runs: [run('COMPLETED')], simulations: [], submitted: false }), 'verified')
  assert.equal(strategyStage({ runs: [run('COMPLETED')], simulations: [], submitted: true }), 'submitted')
  assert.equal(strategyStage({ runs: [], simulations: [sim('a', 'RUNNING', '2026-09-10')], submitted: true }), 'running')
})

test('주 버튼은 단계마다 하나고 운용 중이면 가장 최근에 시작한 실행으로 간다', () => {
  assert.deepEqual(primaryAction({ runs: [], simulations: [], submitted: false }), { kind: 'backtest', label: '백테스트 실행' })
  assert.deepEqual(primaryAction({ runs: [run('COMPLETED')], simulations: [sim('old', 'STOPPED', '2026-09-01')], submitted: false }), { kind: 'simulate', label: '모의투자 시작' })
  const live = primaryAction({ runs: [], simulations: [sim('old', 'RUNNING', '2026-09-01'), sim('new', 'RUNNING', '2026-09-10')], submitted: false })
  assert.equal(live.kind, 'live')
  assert.equal(live.simulationId, 'new')
})

test('전략별 모의투자는 이 전략 것만 최근 시작 순이고 시작 전은 맨 위다', () => {
  const others = [{ id: 'x', strategyId: 's2', status: 'RUNNING', startedAt: new Date('2026-09-12'), stoppedAt: null }]
  const mine = simulationsForStrategy([...others, sim('b', 'STOPPED', '2026-09-05'), sim('c', 'CREATED', null), sim('a', 'RUNNING', '2026-09-10')], 's1')
  assert.deepEqual(mine.map((item) => item.id), ['c', 'a', 'b'])
})
