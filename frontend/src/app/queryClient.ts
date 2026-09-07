import { QueryClient } from '@tanstack/react-query'

import { isApiError } from '../api/error'

/* 4xx 는 다시 보내도 결과가 같으므로 재시도하지 않는다. 네트워크 장애와 5xx 만 한 번 더 시도한다. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => !(isApiError(error) && error.status < 500) && failureCount < 1,
      refetchOnWindowFocus: false,
    },
  },
})
