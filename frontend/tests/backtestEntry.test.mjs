import assert from 'node:assert/strict'
import { test } from 'node:test'
import { hasPeriod, readBacktestEntry } from '../src/strategy/backtestEntry.ts'

test('백테스트 진입 상태는 전략만 있어도 되고 기간은 둘 다 있을 때만 붙는다', () => {
  assert.deepEqual(readBacktestEntry({ strategyId: '7' }), { strategyId: '7' })
  assert.deepEqual(readBacktestEntry({ strategyId: '7', periodStart: '2026-01-01' }), { strategyId: '7' })
  const full = readBacktestEntry({ strategyId: '7', periodStart: '2026-01-01', periodEnd: '2026-03-01', initialCapital: 5_000_000 })
  assert.deepEqual(full, { strategyId: '7', periodStart: '2026-01-01', periodEnd: '2026-03-01', initialCapital: 5_000_000 })
  assert.equal(hasPeriod(full), true)
  assert.equal(hasPeriod(readBacktestEntry({ strategyId: '7' })), false)
  assert.equal(readBacktestEntry({ strategyId: '7', initialCapital: -1 }).initialCapital, undefined)
  assert.equal(readBacktestEntry(null), null)
  assert.equal(readBacktestEntry({ strategyId: '' }), null)
})
