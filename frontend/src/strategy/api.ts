import { request } from '../api/client'
import type {
  CreateStrategyRequest,
  StrategyResponse,
  TranslateStrategyRequest,
  TranslateStrategyResponse,
} from './types'

/* 자연어를 전략 DSL 로 바꾼다. Spring 이 AI 서버를 대신 부르므로 브라우저는 AI 서버를 모른다. */
export function translateStrategy(body: TranslateStrategyRequest): Promise<TranslateStrategyResponse> {
  return request<TranslateStrategyResponse>('/ai/strategies/translate', { method: 'POST', body })
}

/* 전략을 새로 만든다. 뿌리의 첫 버전이다. */
export function createStrategy(body: CreateStrategyRequest): Promise<StrategyResponse> {
  return request<StrategyResponse>('/strategies', { method: 'POST', body })
}

/*
 * 수정 저장. 전략 행은 바뀌지 않으므로 같은 전략의 새 버전을 만든다. 버전 번호는 서버가 정한다.
 * 이미 돌린 백테스트·모의투자는 이전 버전을 그대로 가리킨다.
 */
export function reviseStrategy(strategyId: string, body: CreateStrategyRequest): Promise<StrategyResponse> {
  return request<StrategyResponse>(`/strategies/${encodeURIComponent(strategyId)}/revisions`, { method: 'POST', body })
}

/* 로그인한 회원의 전략만 온다. 회원 식별은 서버가 인증 주체에서 꺼낸다. */
export function fetchStrategies(): Promise<StrategyResponse[]> {
  return request<StrategyResponse[]>('/strategies')
}

export function fetchStrategy(strategyId: string): Promise<StrategyResponse> {
  return request<StrategyResponse>(`/strategies/${encodeURIComponent(strategyId)}`)
}

/* 공개 여부를 바꾼다. 서버가 바뀐 전략을 그대로 돌려주므로 화면이 상태를 따로 만들지 않는다. */
export function changeStrategyVisibility(strategyId: string, published: boolean): Promise<StrategyResponse> {
  return request<StrategyResponse>(`/strategies/${encodeURIComponent(strategyId)}/visibility`, {
    method: 'PATCH',
    body: { published },
  })
}

/* 복제본은 새 전략이라 서버가 201 과 함께 새 식별자를 준다. */
export function copyStrategy(strategyId: string): Promise<StrategyResponse> {
  return request<StrategyResponse>(`/strategies/${encodeURIComponent(strategyId)}/copy`, { method: 'POST' })
}

export function deleteStrategy(strategyId: string): Promise<void> {
  return request<void>(`/strategies/${encodeURIComponent(strategyId)}`, { method: 'DELETE' })
}
