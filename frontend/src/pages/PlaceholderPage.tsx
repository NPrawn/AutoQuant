/*
 * 화면이 구현되기 전까지 라우트가 비어 보이지 않게 하는 자리다.
 * 각 화면 작업에서 실제 컴포넌트로 바꾸면서 이 파일의 사용처는 사라진다.
 */
type PlaceholderPageProps = {
  title: string
  mockup: string
}

export function PlaceholderPage({ title, mockup }: PlaceholderPageProps) {
  return (
    <section style={{ padding: '48px 40px' }}>
      <p className="eyebrow">구현 예정</p>
      <h1 style={{ marginTop: 6, fontSize: 24, letterSpacing: '-0.03em' }}>{title}</h1>
      <p className="mono" style={{ marginTop: 8, color: 'var(--ink-3)', fontSize: 12 }}>
        목업 {mockup}
      </p>
    </section>
  )
}
