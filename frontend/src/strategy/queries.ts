import { useMutation, useQueryClient } from '@tanstack/react-query'

import { createStrategy, translateStrategy } from './api'

/* 전략 목록·상세 쿼리의 키 뿌리. 저장이 성공하면 이 키 아래를 무효화한다. */
export const strategiesQueryKey = ['strategies'] as const

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
