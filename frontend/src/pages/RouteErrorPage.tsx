import { Link, isRouteErrorResponse, useRouteError } from 'react-router'

import { routes } from '../app/routes'
import { ErrorState } from '../components/StateBlock'

/*
 * 화면 하나가 렌더 중에 던진 예외를 셸 안에서 받는다. 이게 없으면 react-router 기본 오류 화면으로
 * 떨어져 사이드바까지 사라지고 빠져나올 길이 없다. 서버 enum 이 하나 늘어 Record 조회가 깨질 때가 흔한 경로다.
 */
export function RouteErrorPage() {
  const error = useRouteError()
  const description = isRouteErrorResponse(error)
    ? `${error.status} ${error.statusText}`
    : '화면을 그리는 중에 문제가 생겼습니다. 다시 열어도 같으면 새로고침해 주세요.'

  return (
    <section className="view active" id="view-route-error">
      <ErrorState
        title="화면을 표시하지 못했습니다"
        description={description}
        error={error}
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
