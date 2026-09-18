import type { PeriodBounds } from './periodBounds.ts'
import { toDateInput } from './periodBounds.ts'
import type { CreateBacktestRunRequest, ExecutionPriceRule } from './types'

/*
 * 백테스트 실행 조건. 목업 10 의 좌측 패널이 편집하는 값이다.
 * 봉 주기와 리밸런싱 주기는 요청에 없다. 백엔드가 전략 DSL 에서 가져가므로 화면은 읽기 전용으로 보여 준다.
 */
export type BacktestRunForm = {
  strategyId: string
  periodStart: string
  periodEnd: string
  initialCapital: number
}

/* 기간 프리셋. 운영 캔들이 몇 달치라 연 단위 대신 월 단위로 둔다. */
export type PresetRange = 'all' | '3m' | '1m'

/* 서비스 고정 정책. 목업이 명시한 값이라 화면에서 바꾸지 않는다. */
export const quoteCurrency = 'KRW'
export const feeRate = 0.0005
export const slippageBps = 5
export const executionPriceRule: ExecutionPriceRule = 'NEXT_CANDLE_OPEN'

/*
 * 엔진과 시장 데이터 버전은 서버가 아는 값인데 요청 필드로 열려 있다.
 * 프론트가 정할 수 없어 상수로 보내고, 서버가 채우도록 백엔드에 요청한다.
 */
export const engineVersion = 'v0.9.2'
export const marketDataVersion = 'v1.4'

export const defaultInitialCapital = 3_000_000

/* 종료일은 오늘까지다. 아직 오지 않은 구간은 캔들이 없어 계산할 수 없다. */
export function createRunForm(strategyId: string, today: Date, bounds: PeriodBounds): BacktestRunForm {
  return {
    strategyId,
    periodStart: bounds.earliestStart,
    periodEnd: toDateInput(today),
    initialCapital: defaultInitialCapital,
  }
}

function shiftMonths(value: Date, months: number): Date {
  const shifted = new Date(value)
  shifted.setMonth(shifted.getMonth() + months)
  return shifted
}

/* 프리셋은 시작일만 옮기고 하한 아래로는 내려가지 않는다. */
export function applyPreset(form: BacktestRunForm, preset: PresetRange, today: Date, bounds: PeriodBounds): BacktestRunForm {
  const end = toDateInput(today)
  if (preset === 'all') {
    return { ...form, periodStart: bounds.earliestStart, periodEnd: end }
  }
  const start = toDateInput(shiftMonths(today, preset === '3m' ? -3 : -1))
  return { ...form, periodStart: start < bounds.earliestStart ? bounds.earliestStart : start, periodEnd: end }
}

/* 지금 값이 어떤 프리셋과 같은지 알려 준다. 직접 고친 구간은 어느 프리셋도 켜지 않는다. */
export function detectPreset(form: BacktestRunForm, today: Date, bounds: PeriodBounds): PresetRange | null {
  const presets: PresetRange[] = ['all', '3m', '1m']
  return (
    presets.find((preset) => {
      const applied = applyPreset(form, preset, today, bounds)
      return applied.periodStart === form.periodStart && applied.periodEnd === form.periodEnd
    }) ?? null
  )
}

export function setStrategyId(form: BacktestRunForm, strategyId: string): BacktestRunForm {
  return { ...form, strategyId }
}

export function setPeriodStart(form: BacktestRunForm, periodStart: string): BacktestRunForm {
  return { ...form, periodStart }
}

export function setPeriodEnd(form: BacktestRunForm, periodEnd: string): BacktestRunForm {
  return { ...form, periodEnd }
}

export function setInitialCapital(form: BacktestRunForm, initialCapital: number): BacktestRunForm {
  return { ...form, initialCapital }
}

/* 하한보다 이른 시작일을 하한으로 끌어올린다. 전략을 바꿔 워밍업이 늘어났을 때 쓴다. */
export function clampPeriodStart(form: BacktestRunForm, bounds: PeriodBounds): BacktestRunForm {
  return form.periodStart < bounds.earliestStart ? { ...form, periodStart: bounds.earliestStart } : form
}

export type RunFormIssue = {
  field: 'strategyId' | 'periodStart' | 'periodEnd' | 'initialCapital'
  message: string
}

/* 백엔드 Bean Validation 과 같은 규칙에 기간 순서와 워밍업 하한을 더해 화면에서 먼저 알린다. */
export function validateRunForm(form: BacktestRunForm, today: Date, bounds: PeriodBounds): RunFormIssue[] {
  const issues: RunFormIssue[] = []

  if (form.strategyId === '') {
    issues.push({ field: 'strategyId', message: '백테스트할 전략을 선택해 주세요.' })
  }
  if (form.periodStart === '') {
    issues.push({ field: 'periodStart', message: '시작일을 입력해 주세요.' })
  } else if (form.periodStart < bounds.earliestStart) {
    issues.push({ field: 'periodStart', message: earliestStartMessage(bounds) })
  }
  if (form.periodEnd === '') {
    issues.push({ field: 'periodEnd', message: '종료일을 입력해 주세요.' })
  } else if (form.periodEnd > toDateInput(today)) {
    issues.push({ field: 'periodEnd', message: '종료일은 오늘까지만 선택할 수 있습니다.' })
  }
  if (form.periodStart !== '' && form.periodEnd !== '' && form.periodStart >= form.periodEnd) {
    issues.push({ field: 'periodEnd', message: '종료일이 시작일보다 뒤여야 합니다.' })
  }
  if (!Number.isFinite(form.initialCapital) || form.initialCapital <= 0) {
    issues.push({ field: 'initialCapital', message: '초기자산을 0보다 큰 금액으로 입력해 주세요.' })
  }

  return issues
}

/* 왜 그 날짜부터인지 함께 알린다. 하한이 밀린 이유는 워밍업이라 그 수를 그대로 보인다. */
export function earliestStartMessage(bounds: PeriodBounds): string {
  if (bounds.coverage === null || bounds.warmupCandles === 0) {
    return `시작일은 ${bounds.earliestStart} 이후여야 합니다.`
  }
  return `이 전략은 판단에 앞선 봉 ${bounds.warmupCandles}개가 필요합니다. 캔들이 ${bounds.coverage.start}부터 있어 ${bounds.earliestStart}부터 평가할 수 있습니다.`
}

/*
 * 사용자가 고른 날짜는 자기 시간대의 하루를 뜻한다. 오프셋 없는 문자열은 로컬 시각으로 해석되므로
 * 그 경계를 그대로 Instant 로 바꾼다. UTC 자정으로 보내면 표시할 때 하루가 밀린다.
 */
function toInstant(date: string, endOfDay: boolean): string {
  return new Date(`${date}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}`).toISOString()
}

export function toCreateRequest(form: BacktestRunForm): CreateBacktestRunRequest {
  return {
    strategyId: Number(form.strategyId),
    periodStart: toInstant(form.periodStart, false),
    periodEnd: toInstant(form.periodEnd, true),
    quoteCurrency,
    initialCapital: form.initialCapital,
    feeRate,
    slippageBps,
    executionPriceRule,
    engineVersion,
    marketDataVersion,
  }
}

export { toDateInput } from './periodBounds.ts'
