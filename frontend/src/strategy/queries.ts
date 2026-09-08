import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { createStrategy, deleteStrategy, fetchStrategies, fetchStrategy, translateStrategy } from './api'
import { toStrategy } from './types'

/* 전략 목록·상세 쿼리의 키 뿌리. 저장·삭제가 성공하면 이 키 아래를 무효화한다. */
export const strategiesQueryKey = ['strategies'] as const
export const strategyQueryKey = (strategyId: string) => [...strategiesQueryKey, strategyId] as const

export function useStrategies() {
  return useQuery({
    queryKey: strategiesQueryKey,
    queryFn: fetchStrategies,
    select: (responses) => responses.map(toStrategy),
  })
}

/* 식별자가 없으면(새 전략 화면) 요청하지 않는다. */
export function useStrategy(strategyId: string | undefined) {
  return useQuery({
    queryKey: strategyQueryKey(strategyId ?? ''),
    queryFn: () => fetchStrategy(strategyId ?? ''),
    select: toStrategy,
    enabled: strategyId !== undefined,
  })
}

/*
 * 변환은 같은 입력에도 답이 달라질 수 있는 쓰기 성격의 호출이라 쿼리가 아니라 뮤테이션이다.
 * 캐시하지 않고 재시도도 하지 않는다. 실패하면 화면이 사용자에게 다시 시도할지 묻는다.
 */
export function useTranslateStrategy() {
  return useMutation({ mutationFn: translateStrategy })
}

export function useCreateStrategy() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: createStrategy,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: strategiesQueryKey }),
  })
}

export function useDeleteStrategy() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: deleteStrategy,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: strategiesQueryKey }),
  })
}
