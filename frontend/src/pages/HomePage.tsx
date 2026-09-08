import type { CSSProperties, FormEvent, KeyboardEvent } from 'react'
import { useState } from 'react'
import { useNavigate } from 'react-router'

import { routes } from '../app/routes'
import type { BuilderEntryState } from '../strategy/builderEntry'

/* 배경 장식 막대. [x%, y%, 너비px, 높이px, 방향, 오른쪽 배치]. 목업 좌표를 그대로 옮겼다. */
type MarketBar = readonly [number, number, number, number, 'up' | 'down', boolean]

const marketBars: readonly MarketBar[] = [
  [2, 64, 17, 88, 'up', false], [5, 19, 24, 43, 'down', false], [8, 79, 14, 66, 'down', false],
  [12, 6, 20, 74, 'up', false], [10, 44, 15, 93, 'up', false], [16, 90, 26, 40, 'down', false],
  [19, 31, 18, 79, 'down', false], [22, 58, 15, 91, 'up', false], [15, 71, 23, 49, 'up', false],
  [25, 14, 16, 68, 'down', false], [28, 47, 21, 84, 'up', false], [31, 76, 14, 57, 'down', false],
  [24, 88, 19, 62, 'down', false],
  [3, 25, 17, 84, 'down', true], [6, 52, 25, 45, 'up', true], [9, 11, 14, 71, 'up', true],
  [13, 71, 21, 58, 'down', true], [11, 38, 16, 90, 'down', true], [17, 86, 27, 41, 'up', true],
  [20, 17, 18, 76, 'up', true], [23, 60, 15, 88, 'down', true], [16, 45, 22, 52, 'down', true],
  [27, 80, 16, 64, 'up', true], [30, 29, 20, 81, 'down', true],
]

/* base.css 의 .market-decor .cbar 가 이 변수들로 위치와 크기, 색을 정한다. */
function barStyle([x, y, width, height, direction]: MarketBar): CSSProperties {
  return {
    '--x': `${x}%`,
    '--y': `${y}%`,
    '--w': `${width}px`,
    '--h': `${height}px`,
    '--c': `var(--${direction})`,
  } as CSSProperties
}

/* 사용자가 무엇을 적을 수 있는지 보여 주는 예시. 누르면 입력창에 그대로 들어간다. */
const examplePrompts = [
  '거래량 터지면서 추세 타는 코인 골라줘',
  '덜 흔들리는 코인만 골라서 안전하게',
  '많이 오른 코인 3개에 공격적으로',
  '고점에서 크게 빠진 코인은 빼줘',
] as const

/* 백엔드 변환 요청의 userInput 상한과 같다. */
const promptMaxLength = 500

/*
 * 로그인 뒤 첫 화면. 목표 문장을 받아 빌더로 넘긴다.
 * base.css 가 #view-home 과 main.wrap:has(#view-home.active) 로 배경과 여백을 정하므로
 * 목업의 id 를 그대로 둔다. 이 화면은 한 번만 렌더링되니 id 충돌은 없다.
 */
export function HomePage() {
  const navigate = useNavigate()
  const [prompt, setPrompt] = useState('')
  const trimmedPrompt = prompt.trim()

  const submitPrompt = () => {
    if (trimmedPrompt === '') {
      return
    }
    const entry: BuilderEntryState = { prompt: trimmedPrompt }
    navigate(routes.strategyNew, { state: entry })
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    submitPrompt()
  }

  /* textarea 라 Enter 는 줄바꿈이다. Ctrl 또는 Cmd 와 함께 누르면 제출한다. */
  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault()
      submitPrompt()
    }
  }

  return (
    <section className="view active" id="view-home">
      <div className="market-decor" aria-hidden="true">
        {marketBars.map((bar) => (
          <i key={`${bar[5] ? 'r' : 'l'}-${bar[0]}-${bar[1]}`} className={bar[5] ? 'cbar right' : 'cbar'} style={barStyle(bar)} />
        ))}
      </div>

      <div className="hero-in">
        <span className="hero-badge">
          <i aria-hidden="true" />
          UPBIT KRW · AUTOQUANT
        </span>
        <h1>
          <span>투자 아이디어를</span>
          <span>전략으로.</span>
        </h1>
        <p className="sub">
          어떤 코인을 살지 고르지 않습니다. 원하는 목표만 적으면 후보를 걸러내고 순위를 매기는 규칙이 만들어지고,
          종목은 전략이 매 리밸런싱 시점에 다시 뽑습니다.
        </p>

        <form className="prompt-box" onSubmit={handleSubmit}>
          <label htmlFor="heroPrompt" className="sr-only">
            투자 목표
          </label>
          <textarea
            id="heroPrompt"
            rows={2}
            maxLength={promptMaxLength}
            placeholder="예: 거래량 터지면서 추세 타는 코인 골라줘 / 덜 흔들리는 코인만 안전하게"
            value={prompt}
            onChange={(event) => setPrompt(event.target.value)}
            onKeyDown={handleKeyDown}
          />
          <div className="prompt-foot">
            <span className="prompt-hint">목표만 적으면 후보 조건 · 순위 기준 · 종목 수까지 채워 초안을 만듭니다</span>
            <button className="btn primary" type="submit" disabled={trimmedPrompt === ''}>
              전략 만들기
              <svg viewBox="0 0 24 24" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M5 12h13M13 6l6 6-6 6" />
              </svg>
            </button>
          </div>
        </form>

        <div className="chips" role="group" aria-label="예시 목표">
          {examplePrompts.map((example) => (
            <button key={example} className="chip-ex" type="button" onClick={() => setPrompt(example)}>
              {example}
            </button>
          ))}
        </div>

        <p className="hero-note">만든 초안은 블록에서 값을 조정한 뒤 저장하면 v1이 됩니다.</p>
      </div>
    </section>
  )
}
