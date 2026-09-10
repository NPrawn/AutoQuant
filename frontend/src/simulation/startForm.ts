import { defaultInitialCapital, engineVersion, executionPriceRule, feeRate, slippageBps } from '../backtest/runForm.ts'
import type { CreateSimulationRunRequest } from './types'

/*
 * 모의투자 시작 조건. 목업 16 의 상단 패널이 편집하는 값이다. 기간은 없다. 시작 시점부터
 * 실시간 봉을 받는다. 비용과 체결가 규칙은 백테스트와 같은 서비스 고정값을 쓴다.
 */
export type SimulationStartForm = {
  strategyId: string
  initialCapital: number
}

export function createStartForm(strategyId: string): SimulationStartForm {
  return { strategyId, initialCapital: defaultInitialCapital }
}

export function setStrategyId(form: SimulationStartForm, strategyId: string): SimulationStartForm {
  return { ...form, strategyId }
}

export function setInitialCapital(form: SimulationStartForm, initialCapital: number): SimulationStartForm {
  return { ...form, initialCapital }
}

export type StartFormIssue = {
  field: 'strategyId' | 'initialCapital'
  message: string
}

/* 백엔드가 SIMULATION-400-001 로 한 번에 거부하는 규칙을 필드별로 먼저 알린다. */
export function validateStartForm(form: SimulationStartForm): StartFormIssue[] {
  const issues: StartFormIssue[] = []
  if (form.strategyId === '') {
    issues.push({ field: 'strategyId', message: '운용할 전략을 선택해 주세요.' })
  }
  if (!Number.isFinite(form.initialCapital) || form.initialCapital <= 0) {
    issues.push({ field: 'initialCapital', message: '초기 모의자산을 0보다 큰 금액으로 입력해 주세요.' })
  }
  return issues
}

export function toCreateRequest(form: SimulationStartForm): CreateSimulationRunRequest {
  return {
    strategyId: Number(form.strategyId),
    initialCapital: form.initialCapital,
    feeRate,
    slippageBps,
    executionPriceRule,
    engineVersion,
  }
}
