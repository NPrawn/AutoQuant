import assert from 'node:assert/strict'
import { test } from 'node:test'
import { targetRoute } from '../src/notification/targetRoute.ts'

test('알림 종류별 이동 경로. 대회 채점은 회차 결과로 가고 대상이 없거나 모르는 종류는 null 이다', () => {
  assert.equal(targetRoute({ type: 'BACKTEST_COMPLETED', targetId: 'abc' }), '/backtest?run=abc')
  assert.equal(targetRoute({ type: 'BACKTEST_FAILED', targetId: 'abc' }), '/backtest?run=abc')
  assert.equal(targetRoute({ type: 'SIMULATION_FAILED', targetId: 'sim-1' }), '/simulations/sim-1')
  assert.equal(targetRoute({ type: 'CONTEST_SCORED', targetId: 'round-1' }), '/contests/round-1/result')
  assert.equal(targetRoute({ type: 'BACKTEST_COMPLETED', targetId: null }), null)
  assert.equal(targetRoute({ type: 'SOMETHING_NEW', targetId: 'x' }), null)
})
