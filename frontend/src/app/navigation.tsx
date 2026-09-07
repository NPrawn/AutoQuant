import type { ReactNode } from 'react'

import { routes } from './routes'

/*
 * 사이드바 항목. 목업의 두 구역(탐색, 운용)을 그대로 따른다.
 * 아이콘은 목업 SVG 의 path 만 옮겼고 크기와 색은 base.css 의 .navbtn svg 가 정한다.
 */
export type NavItem = {
  label: string
  to: string
  icon: ReactNode
  /* 홈은 모든 경로의 접두사라서 정확히 일치할 때만 활성으로 본다. */
  end?: boolean
}

export type NavSection = {
  label: string
  items: NavItem[]
}

export const navSections: NavSection[] = [
  {
    label: '탐색',
    items: [
      {
        label: '전략 만들기',
        to: routes.home,
        end: true,
        icon: (
          <>
            <path d="M4 11l8-7 8 7" />
            <path d="M6 10v9h12v-9" />
          </>
        ),
      },
      {
        label: '차트',
        to: routes.chart,
        icon: (
          <>
            <line x1="6" y1="3" x2="6" y2="8" />
            <rect x="4" y="8" width="4" height="7" rx="1" />
            <line x1="6" y1="15" x2="6" y2="21" />
            <line x1="13" y1="5" x2="13" y2="10" />
            <rect x="11" y="10" width="4" height="6" rx="1" />
            <line x1="13" y1="16" x2="13" y2="20" />
            <line x1="20" y1="4" x2="20" y2="9" />
            <rect x="18" y="9" width="4" height="6" rx="1" />
            <line x1="20" y1="15" x2="20" y2="20" />
          </>
        ),
      },
      {
        label: '대회',
        to: routes.contests,
        icon: (
          <>
            <circle cx="12" cy="9" r="5.2" />
            <path d="M9 13.6L7.2 21l4.8-2.8 4.8 2.8-1.8-7.4" />
          </>
        ),
      },
      {
        label: '전략 랭킹',
        to: routes.ranking,
        icon: <path d="M5 21V12M12 21V4M19 21v-6" />,
      },
    ],
  },
  {
    label: '운용',
    items: [
      {
        label: '전략 관리',
        to: routes.strategies,
        icon: (
          <>
            <rect x="4" y="4" width="7" height="7" rx="1.5" />
            <rect x="13" y="4" width="7" height="7" rx="1.5" />
            <rect x="4" y="13" width="7" height="7" rx="1.5" />
            <rect x="13" y="13" width="7" height="7" rx="1.5" />
          </>
        ),
      },
      {
        label: '백테스트',
        to: routes.backtest,
        icon: <path d="M9 3h6M10 3v5.5L4.8 17a2 2 0 0 0 1.7 3h11a2 2 0 0 0 1.7-3L14 8.5V3" />,
      },
      {
        label: '모의투자',
        to: routes.simulations,
        icon: (
          <>
            <circle cx="12" cy="12" r="9" />
            <path d="M10 8.5l6 3.5-6 3.5z" />
          </>
        ),
      },
    ],
  },
]
