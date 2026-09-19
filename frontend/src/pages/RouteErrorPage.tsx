import { useEffect } from 'react'
import { Link, isRouteErrorResponse, useRouteError } from 'react-router'

import { routes } from '../app/routes'
import { ErrorState } from '../components/StateBlock'
import { setRouteErrorShown } from '../app/routeErrorState'

/*
 * 화면 하나가 렌더 중에 던진 예외를 셸 안에서 받는다. 이게 없으면 react-router 기본 오류 화면으로
 * 떨어져 사이드바까지 사라지고 빠져나올 길이 없다. 서버 enum 이 하나 늘어 Record 조회가 깨질 때가 흔한 경로다.
 */
export function RouteErrorPage() {
  const error = useRouteError()
  /* 상단바는 라우트 handle 로 제목을 읽는데 오류 상태에서도 원래 화면이 매치돼 제목이 남는다. 여기서 알린다. */
  useEffect(() => {
    setRouteErrorShown(true)
    return () => setRouteErrorShown(false)
  }, [])
  const description = isRouteErrorResponse(error)
    ? `${error.status} ${error.statusText}`
    : '화면을 그리는 중에 문제가 생겼습니다. 다시 열어도 같으면 새로고침해 주세요.'

  return (
    <section className="view active" id="view-route-error">
      <ErrorState
        title="화면을 표시하지 못했습니다"
        description={description}
        error={error}
        retryLabel="새로고침"
        onRetry={() => window.location.reload()}
        actions={
          <Link className="btn" to={routes.home}>
            전략 만들기로
          </Link>
        }
      />
    </section>
  )
}
