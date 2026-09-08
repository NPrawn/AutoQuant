import { request } from '../api/client'
import type { TranslateStrategyRequest, TranslateStrategyResponse } from './types'

/* 자연어를 전략 DSL 로 바꾼다. Spring 이 AI 서버를 대신 부르므로 브라우저는 AI 서버를 모른다. */
export function translateStrategy(body: TranslateStrategyRequest): Promise<TranslateStrategyResponse> {
  return request<TranslateStrategyResponse>('/ai/strategies/translate', { method: 'POST', body })
}
