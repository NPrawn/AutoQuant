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

/* 전략을 새로 만든다. 수정 API 는 아직 없어 편집 화면의 저장도 이 함수로 새 전략을 만든다. */
export function createStrategy(body: CreateStrategyRequest): Promise<StrategyResponse> {
  return request<StrategyResponse>('/strategies', { method: 'POST', body })
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
