import assert from 'node:assert/strict'
import { test } from 'node:test'
import { notificationLooks, notificationTime } from '../src/notification/labels.ts'

/* 로컬 시간 기준이라 시험도 로컬로 만든다. 표기 규칙이 로컬 자정 경계를 쓰기 때문이다. */
const at = (year, month, day, hour = 0, minute = 0) => new Date(year, month - 1, day, hour, minute)

test('오늘 온 알림은 시:분으로 읽는다', () => {
  const now = at(2026, 9, 16, 18, 30)
  assert.equal(notificationTime(at(2026, 9, 16, 15, 4), now), '15:04')
  assert.equal(notificationTime(at(2026, 9, 16, 0, 0), now), '00:00')
})

test('어제 온 알림은 어제로 읽는다', () => {
  const now = at(2026, 9, 16, 9, 0)
  assert.equal(notificationTime(at(2026, 9, 15, 23, 59), now), '어제')
  assert.equal(notificationTime(at(2026, 9, 15, 0, 1), now), '어제')
})

test('그 앞은 며칠 전인지로 읽고 한 주가 넘으면 날짜로 읽는다', () => {
  const now = at(2026, 9, 16, 9, 0)
  assert.equal(notificationTime(at(2026, 9, 14, 12, 0), now), '2일 전')
  assert.equal(notificationTime(at(2026, 9, 11, 12, 0), now), '5일 전')
  assert.equal(notificationTime(at(2026, 9, 9, 12, 0), now), '2026-09-09')
})

test('실패는 alert, 완료는 done 으로 색 계약을 지킨다', () => {
  assert.equal(notificationLooks.BACKTEST_FAILED.tone, 'alert')
  assert.equal(notificationLooks.SIMULATION_FAILED.tone, 'alert')
  assert.equal(notificationLooks.CONTEST_SCORED.tone, 'contest')
  assert.equal(notificationLooks.CONTEST_SCORED.action, '결과 보기')
  assert.equal(notificationLooks.BACKTEST_COMPLETED.tone, 'done')
})
