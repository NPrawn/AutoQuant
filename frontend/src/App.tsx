import { ThemeToggle } from './styles/ThemeToggle'

/*
 * 디자인 토큰이 적용됐는지 눈으로 확인하는 임시 화면이다.
 * 라우터를 도입할 때 실제 화면 구성으로 대체한다.
 */
function App() {
  return (
    <main style={{ padding: '48px 40px', maxWidth: 900, margin: '0 auto' }}>
      <header style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <h1 style={{ fontSize: 30, letterSpacing: '-0.04em' }}>AutoQuant</h1>
        <ThemeToggle />
      </header>
      <p className="eyebrow" style={{ marginTop: 8 }}>
        디자인 토큰 적용 확인
      </p>
      <div className="card" style={{ marginTop: 24, padding: 20 }}>
        <p>
          상승 <span className="updown up num">+3.24%</span> · 하락{' '}
          <span className="updown down num">-1.08%</span>
        </p>
      </div>
    </main>
  )
}

export default App
