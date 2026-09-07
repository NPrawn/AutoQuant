import type { CSSProperties, FormEvent } from 'react'
import { useSearchParams } from 'react-router'

import { describeLoginError, loginStartFailedMessage } from '../auth/loginErrorMessage'
import { useStartLogin } from '../auth/queries'
import { ThemeToggle } from '../styles/ThemeToggle'

/* 배경 장식 막대. [x%, y%, 너비px, 높이px, 방향]. 목업 좌표를 그대로 옮겼다. */
type DecorBar = readonly [number, number, number, number, 'up' | 'down']

const decorBars: readonly DecorBar[] = [
  [3, 58, 17, 86, 'up'], [6, 21, 25, 44, 'down'], [9, 74, 14, 62, 'down'], [13, 7, 21, 71, 'up'],
  [11, 41, 16, 95, 'up'], [17, 88, 27, 39, 'down'], [20, 29, 15, 78, 'down'], [23, 63, 23, 52, 'up'],
  [19, 12, 18, 66, 'up'], [27, 47, 14, 91, 'down'], [31, 81, 26, 43, 'down'], [29, 23, 19, 74, 'up'],
  [35, 55, 16, 88, 'up'], [38, 9, 24, 48, 'down'], [33, 71, 15, 64, 'down'], [42, 37, 20, 82, 'up'],
  [45, 66, 14, 93, 'up'], [40, 17, 27, 41, 'down'], [48, 79, 18, 58, 'down'], [51, 27, 16, 86, 'up'],
  [55, 52, 25, 46, 'up'], [58, 13, 15, 76, 'down'], [53, 87, 21, 54, 'down'], [62, 41, 14, 92, 'up'],
  [66, 68, 26, 42, 'up'], [64, 19, 18, 71, 'down'], [70, 49, 16, 84, 'down'], [74, 8, 23, 51, 'up'],
  [69, 84, 15, 61, 'up'], [78, 33, 20, 78, 'down'], [82, 62, 14, 94, 'down'], [76, 74, 27, 38, 'up'],
  [85, 15, 18, 68, 'up'], [88, 45, 16, 87, 'down'], [92, 77, 24, 44, 'down'], [95, 26, 15, 72, 'up'],
  [90, 61, 21, 56, 'up'], [97, 49, 17, 81, 'down'], [44, 90, 15, 49, 'down'], [7, 90, 19, 63, 'up'],
]

/* base.css 의 .login-decor i 가 이 변수들로 위치와 크기, 색을 정한다. */
function decorStyle([x, y, width, height, direction]: DecorBar): CSSProperties {
  return {
    '--x': `${x}%`,
    '--y': `${y}%`,
    '--w': `${width}px`,
    '--h': `${height}px`,
    '--c': `var(--${direction})`,
  } as CSSProperties
}

export function LoginPage() {
  const [searchParams] = useSearchParams()
  const startLogin = useStartLogin()

  /* 콜백이 붙여 보낸 오류가 우선이고, 없으면 인가 URL 조회 실패를 보인다. */
  const errorMessage =
    describeLoginError(searchParams.get('error')) ?? (startLogin.isError ? loginStartFailedMessage : null)
  const pending = startLogin.isPending || startLogin.isSuccess

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    startLogin.mutate()
  }

  return (
    <div className="login">
      <ThemeToggle />

      <div className="login-decor" aria-hidden="true">
        {decorBars.map((bar) => (
          <i key={`${bar[0]}-${bar[1]}`} style={decorStyle(bar)} />
        ))}
      </div>

      <div className="login-center">
        <div className="login-brand">
          <i aria-hidden="true" />
          <span>AutoQuant</span>
        </div>
        <h1>
          투자 아이디어를
          <br />
          전략으로.
        </h1>
        <p>목표만 적으면 후보를 걸러내고 순위를 매기는 규칙이 만들어집니다.</p>

        <form className="login-form" onSubmit={handleSubmit}>
          {errorMessage && (
            <div className="callout crit" role="alert">
              <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <circle cx="12" cy="12" r="9" />
                <path d="M12 8v5M12 16h.01" />
              </svg>
              <span>{errorMessage}</span>
            </div>
          )}
          <button className="btn auth-submit sso-btn" type="submit" disabled={pending} aria-busy={pending}>
            <i aria-hidden="true">S</i>
            {pending ? 'SSAFY로 이동 중…' : 'SSAFY로 계속하기'}
          </button>
          <p className="login-cap">인증 페이지에서 로그인하면 자동으로 돌아옵니다. 비밀번호는 저장하지 않습니다.</p>
        </form>

        <div className="login-foot">
          <span>Upbit KRW 상위 30</span>
          <i aria-hidden="true">·</i>
          <span>Filter → Ranking → Top 5</span>
          <i aria-hidden="true">·</i>
          <span>상승 · 하락 · 횡보 채점</span>
        </div>
      </div>
    </div>
  )
}
