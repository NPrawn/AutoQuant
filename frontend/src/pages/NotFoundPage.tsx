import { Link } from 'react-router'

import { routes } from '../app/routes'

export function NotFoundPage() {
  return (
    <section style={{ padding: '48px 40px' }}>
      <p className="eyebrow">404</p>
      <h1 style={{ marginTop: 6, fontSize: 24, letterSpacing: '-0.03em' }}>페이지를 찾을 수 없습니다</h1>
      <p style={{ marginTop: 8, color: 'var(--ink-3)' }}>
        주소가 바뀌었거나 삭제된 화면입니다.
      </p>
      <p style={{ marginTop: 16 }}>
        <Link to={routes.home}>전략 만들기로 돌아가기</Link>
      </p>
    </section>
  )
}
