import { Link } from 'react-router'

import { routes } from '../app/routes'
import { EmptyState } from '../components/StateBlock'

/* 없는 주소. 셸 안에서 그려져 사이드바로 바로 다른 화면에 갈 수 있다. */
export function NotFoundPage() {
  return (
    <section className="view active" id="view-not-found">
      <EmptyState
        title="화면을 찾을 수 없습니다"
        description="주소가 바뀌었거나 삭제된 화면입니다. 왼쪽 메뉴에서 다른 화면으로 갈 수 있습니다."
        actions={
          <Link className="btn primary" to={routes.home}>
            전략 만들기로
          </Link>
        }
      />
    </section>
  )
}
