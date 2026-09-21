import assert from 'node:assert/strict'
import { test } from 'node:test'

import { forVersions, groupByLineage, lineageIds, lineageOf, pickerLabel, pickerOptions, versionLabel, versionOf } from '../src/strategy/lineage.ts'

const strategy = (id, rootId, version, createdAt = '2026-09-01T00:00:00Z') => ({
  id,
  rootId,
  version,
  name: `전략 ${rootId}`,
  description: '',
  dsl: {},
  published: false,
  createdAt: new Date(createdAt),
})

test('같은 뿌리의 버전들을 한 줄로 묶고 최신 버전이 대표다', () => {
  const lineages = groupByLineage([strategy('1', '1', 1), strategy('2', '2', 1), strategy('3', '1', 2), strategy('5', '1', 3)])

  assert.equal(lineages.length, 2)
  assert.equal(lineages[0].rootId, '1')
  assert.equal(lineages[0].head.id, '5')
  assert.deepEqual(
    lineages[0].versions.map((version) => version.version),
    [3, 2, 1],
  )
  assert.equal(lineages[1].head.id, '2')
})

test('뿌리가 처음 나온 자리에 놓아 서버 순서를 지킨다', () => {
  const lineages = groupByLineage([strategy('9', '9', 1), strategy('4', '2', 2), strategy('2', '2', 1)])

  assert.deepEqual(
    lineages.map((lineage) => lineage.rootId),
    ['9', '2'],
  )
})

test('어느 버전을 보고 있든 그 전략 전체를 찾고 없는 전략은 null 이다', () => {
  const all = [strategy('1', '1', 1), strategy('3', '1', 2), strategy('2', '2', 1)]

  assert.equal(lineageOf(all, '1')?.head.id, '3')
  assert.equal(lineageOf(all, '3')?.versions.length, 2)
  assert.equal(lineageOf(all, '404'), null)
})

test('기록을 거르는 식별자 묶음은 목록이 없으면 보는 버전 하나다', () => {
  const lineage = lineageOf([strategy('1', '1', 1), strategy('3', '1', 2)], '1')

  assert.deepEqual(lineageIds(lineage, '1'), ['3', '1'])
  assert.deepEqual(lineageIds(null, '7'), ['7'])
})

test('버전 번호를 찾고 여러 버전의 기록을 원래 순서대로 거른다', () => {
  const lineage = lineageOf([strategy('1', '1', 1), strategy('3', '1', 2)], '3')
  const runs = [{ id: 'a', strategyId: '3' }, { id: 'b', strategyId: '2' }, { id: 'c', strategyId: '1' }]

  assert.equal(versionOf(lineage, '1'), 1)
  assert.equal(versionOf(lineage, '2'), null)
  assert.deepEqual(
    forVersions(runs, lineageIds(lineage, '3')).map((run) => run.id),
    ['a', 'c'],
  )
  assert.equal(versionLabel(2), 'v2')
})

test('셀렉트에는 최신 버전만 올리고 옛 버전은 들어온 경우에만 함께 올린다', () => {
  const all = [strategy('1', '1', 1), strategy('3', '1', 2), strategy('2', '2', 1)]

  assert.deepEqual(
    pickerOptions(all).map((option) => option.id),
    ['3', '2'],
  )
  assert.deepEqual(
    pickerOptions(all, '1').map((option) => option.id),
    ['3', '2', '1'],
  )
  assert.deepEqual(
    pickerOptions(all, '3').map((option) => option.id),
    ['3', '2'],
  )
  assert.deepEqual(
    pickerOptions(all, '404').map((option) => option.id),
    ['3', '2'],
  )
  assert.equal(pickerLabel(strategy('3', '1', 2)), '전략 1 v2')
})
