import { createBrowserRouter, Outlet } from 'react-router'

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
import { routes } from './routes'

/*
 * 로그인은 사이드바가 없는 화면이라 셸 바깥에 둔다. 나머지 화면은 공통 레이아웃
 * 라우트 아래에 모여 있고, 지금은 Outlet 만 있다가 레이아웃 작업에서 셸로 바뀐다.
 */
export const router = createBrowserRouter([
  { path: routes.login, element: <LoginPage /> },
  {
    element: <Outlet />,
    children: [
      { path: routes.home, element: <HomePage /> },
      { path: routes.chart, element: <ChartPage /> },
      { path: routes.contests, element: <ContestListPage /> },
      { path: routes.contestDetail, element: <ContestDetailPage /> },
      { path: routes.contestResult, element: <ContestResultPage /> },
      { path: routes.ranking, element: <RankingPage /> },
      { path: routes.strategies, element: <StrategyListPage /> },
      // new 와 edit 는 :strategyId 보다 구체적이라 먼저 매칭된다. 순서는 상관없지만 읽기 편하게 앉힌다.
      { path: routes.strategyNew, element: <StrategyBuilderPage /> },
      { path: routes.strategyEdit, element: <StrategyBuilderPage /> },
      { path: routes.strategyDetail, element: <StrategyDetailPage /> },
      { path: routes.backtest, element: <BacktestPage /> },
      { path: routes.simulations, element: <SimulationListPage /> },
      { path: routes.simulationLive, element: <SimulationLivePage /> },
      { path: routes.notifications, element: <NotificationsPage /> },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])
