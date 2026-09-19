import assert from 'node:assert/strict'
import { test } from 'node:test'
import { directionLabel, factorOf, rebalanceOf, timeframeLabel, toTimeframe } from '../src/strategy/labels.ts'

test('서버 값 폴백. 아는 값은 문구를, 모르는 값은 원문이나 중립값을 돌려준다', () => {
  assert.equal(rebalanceOf('WEEKLY').label, '매주')
  assert.deepEqual(rebalanceOf('BIWEEKLY'), { label: 'BIWEEKLY', note: '' })
  assert.equal(timeframeLabel('1D'), '일봉')
  assert.equal(timeframeLabel('2H'), '2H')
  assert.equal(factorOf('RSI').label, 'RSI')
  assert.equal(factorOf('MACD').short, 'MACD')
  assert.equal(directionLabel('DESC'), '높은 순')
  assert.equal(directionLabel('RANDOM'), 'RANDOM')
  assert.equal(toTimeframe('4H'), '4H')
  assert.equal(toTimeframe('bogus'), '1D')
})
