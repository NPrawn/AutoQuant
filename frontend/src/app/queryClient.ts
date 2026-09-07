import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'

import { isApiError } from '../api/error'
import { expireSessionOnUnauthorized } from '../auth/sessionExpiry'

/*
 * 4xx 는 다시 보내도 결과가 같으므로 재시도하지 않는다. 네트워크 장애와 5xx 만 한 번 더
 * 시도한다. 401 은 쿼리든 뮤테이션이든 여기서 한 번 잡아 세션 만료로 처리한다.
 */
export const queryClient: QueryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => !(isApiError(error) && error.status < 500) && failureCount < 1,
      refetchOnWindowFocus: false,
    },
  },
  queryCache: new QueryCache({
    onError: (error) => expireSessionOnUnauthorized(queryClient, error),
  }),
  mutationCache: new MutationCache({
    onError: (error) => expireSessionOnUnauthorized(queryClient, error),
  }),
})
