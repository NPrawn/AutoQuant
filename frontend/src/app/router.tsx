import { createBrowserRouter } from 'react-router'

import { BacktestPage } from '../pages/BacktestPage'
import { ChartPage } from '../pages/ChartPage'
import { ContestDetailPage } from '../pages/ContestDetailPage'
import { ContestListPage } from '../pages/ContestListPage'
import { ContestResultPage } from '../pages/ContestResultPage'
import { HomePage } from '../pages/HomePage'
import { LoginPage } from '../pages/LoginPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { NotificationSettingsPage } from '../pages/NotificationSettingsPage'
import { NotificationsPage } from '../pages/NotificationsPage'
import { RouteErrorPage } from '../pages/RouteErrorPage'
import { RankingPage } from '../pages/RankingPage'
import { SimulationLivePage } from '../pages/SimulationLivePage'
import { SimulationListPage } from '../pages/SimulationListPage'
import { StrategyBuilderPage } from '../pages/StrategyBuilderPage'
import { StrategyDetailPage } from '../pages/StrategyDetailPage'
import { StrategyListPage } from '../pages/StrategyListPage'
import { GuestOnly } from '../auth/GuestOnly'
import { RequireAuth } from '../auth/RequireAuth'
import { AppShell } from './layout/AppShell'
import { routes } from './routes'

/*
 * 로그인은 사이드바가 없는 화면이라 셸 바깥에 두고, 이미 로그인했으면 홈으로 보낸다.
 * 나머지 화면은 RequireAuth 가 로그인 여부를 확인한 뒤 AppShell 아래에 그린다. 404 도 그 안에 둔다.
 * handle.title 은 상단바가 현재 화면 제목으로 읽는다.
 */
export const router = createBrowserRouter([
  {
    element: <GuestOnly />,
    children: [{ path: routes.login, element: <LoginPage /> }],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          {
            /* 화면이 렌더 중 던진 예외는 여기서 받아 셸 안에 보인다. 서버 enum 이 늘어 Record 조회가 깨질 때가 흔한 경로다. */
            errorElement: <RouteErrorPage />,
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
      { path: routes.notificationSettings, element: <NotificationSettingsPage />, handle: { title: '알림 설정' } },
      /* 없는 주소도 셸 안에서 받아 사이드바와 상단바가 남는다. 로그인 전이면 RequireAuth 가 먼저 로그인으로 보낸다. */
      { path: '*', element: <NotFoundPage />, handle: { title: '찾을 수 없음' } },
            ],
          },
        ],
      },
    ],
  },
])
