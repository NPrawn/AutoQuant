import { createBrowserRouter } from 'react-router'

import { BacktestPage } from '../pages/BacktestPage'
import { ChartPage } from '../pages/ChartPage'
import { ContestDetailPage } from '../pages/ContestDetailPage'
import { ContestListPage } from '../pages/ContestListPage'
import { ContestResultPage } from '../pages/ContestResultPage'
import { HomePage } from '../pages/HomePage'
import { LoginPage } from '../pages/LoginPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { NotificationsPage } from '../pages/NotificationsPage'
import { RankingPage } from '../pages/RankingPage'
import { SimulationLivePage } from '../pages/SimulationLivePage'
import { SimulationListPage } from '../pages/SimulationListPage'
import { StrategyBuilderPage } from '../pages/StrategyBuilderPage'
import { StrategyDetailPage } from '../pages/StrategyDetailPage'
import { StrategyListPage } from '../pages/StrategyListPage'
import { AppShell } from './layout/AppShell'
import { routes } from './routes'

/*
 * 로그인은 사이드바가 없는 화면이라 셸 바깥에 둔다. 나머지 화면은 AppShell 아래에
 * 모이고, handle.title 은 상단바가 현재 화면 제목으로 읽는다.
 */
export const router = createBrowserRouter([
  { path: routes.login, element: <LoginPage /> },
  {
    element: <AppShell />,
    children: [
      { path: routes.home, element: <HomePage />, handle: { title: '전략 만들기' } },
      { path: routes.chart, element: <ChartPage />, handle: { title: '차트' } },
      { path: routes.contests, element: <ContestListPage />, handle: { title: '대회' } },
      { path: routes.contestDetail, element: <ContestDetailPage />, handle: { title: '대회 상세' } },
      { path: routes.contestResult, element: <ContestResultPage />, handle: { title: '대회 결과' } },
      { path: routes.ranking, element: <RankingPage />, handle: { title: '전략 랭킹' } },
      { path: routes.strategies, element: <StrategyListPage />, handle: { title: '전략 관리' } },
      { path: routes.strategyNew, element: <StrategyBuilderPage />, handle: { title: '전략 구성' } },
      { path: routes.strategyEdit, element: <StrategyBuilderPage />, handle: { title: '전략 구성' } },
      { path: routes.strategyDetail, element: <StrategyDetailPage />, handle: { title: '전략 상세' } },
      { path: routes.backtest, element: <BacktestPage />, handle: { title: '백테스트' } },
      { path: routes.simulations, element: <SimulationListPage />, handle: { title: '모의투자' } },
      { path: routes.simulationLive, element: <SimulationLivePage />, handle: { title: '모의투자 실시간' } },
      { path: routes.notifications, element: <NotificationsPage />, handle: { title: '알림' } },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])
