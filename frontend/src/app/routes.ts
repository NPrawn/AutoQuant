/*
 * 경로 문자열은 여기에만 둔다. 컴포넌트가 '/strategies/…' 를 직접 쓰면 경로를 바꿀 때
 * 놓치는 곳이 생긴다. routes 는 라우터 정의용 패턴이고 to 는 링크를 만드는 함수다.
 */
export const routes = {
  home: '/',
  login: '/login',
  chart: '/chart',
  contests: '/contests',
  contestDetail: '/contests/:contestId',
  contestResult: '/contests/:contestId/result',
  ranking: '/ranking',
  strategies: '/strategies',
  strategyNew: '/strategies/new',
  strategyDetail: '/strategies/:strategyId/:tab?',
  strategyEdit: '/strategies/:strategyId/edit',
  backtest: '/backtest',
  simulations: '/simulations',
  simulationLive: '/simulations/:simulationId',
  notifications: '/notifications',
} as const

/* 전략 상세의 기록 탭. 기본인 백테스트는 세그먼트를 붙이지 않는다. */
export type StrategyDetailTab = 'simulations'

export const to = {
  contestDetail: (contestId: string) => `/contests/${contestId}`,
  contestResult: (contestId: string) => `/contests/${contestId}/result`,
  strategyDetail: (strategyId: string, tab?: StrategyDetailTab) =>
    tab ? `/strategies/${strategyId}/${tab}` : `/strategies/${strategyId}`,
  strategyEdit: (strategyId: string) => `/strategies/${strategyId}/edit`,
  /* 백테스트 결과는 주소의 run 파라미터가 가리킨다. 이력에서 결과로 갈 때 쓴다. */
  backtestRun: (backtestId: string) => `${routes.backtest}?run=${encodeURIComponent(backtestId)}`,
  simulationLive: (simulationId: string) => `/simulations/${simulationId}`,
} as const
