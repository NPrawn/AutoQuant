import type { MouseEvent } from 'react'
import { Link, useNavigate } from 'react-router'

import { routes, to } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState } from '../components/StateBlock'
import type { BacktestEntryState } from '../strategy/backtestEntry'
import { useStrategies } from '../strategy/queries'
import { summarizeDsl } from '../strategy/summary'
import type { Strategy } from '../strategy/types'

/* sv-SE 로케일은 로컬 시간 기준 YYYY-MM-DD 를 준다. 목업의 날짜 표기와 같다. */
const dateFormat = new Intl.DateTimeFormat('sv-SE')

/*
 * 내 전략 목록(목업 05·23). 상태·수익률·낙폭은 백테스트 결과가 있어야 채울 수 있어
 * 지금은 초안과 빈 값으로 둔다. 백테스트 결과 API 가 생기면 이 표만 바뀐다.
 */
export function StrategyListPage() {
  const navigate = useNavigate()
  const strategies = useStrategies()

  return (
    <section className="view active" id="view-strategies">
      <div className="page-head">
        <div>
          <h1>내 전략</h1>
          <p className="sub">저장할 때마다 새 버전이 만들어집니다. 각 전략의 현재 상태와 다음 할 일만 표시합니다.</p>
        </div>
        <Link className="btn primary" to={routes.home}>
          새 전략 만들기
        </Link>
      </div>

      {strategies.isPending && <LoadingState label="전략을 불러오는 중…" />}
      {strategies.isError && (
        <ErrorState title="전략 목록을 불러오지 못했습니다" error={strategies.error} onRetry={() => strategies.refetch()} />
      )}
      {strategies.isSuccess && strategies.data.length === 0 && (
        <EmptyState
          title="아직 저장된 전략이 없습니다"
          description="원하는 목표를 한 줄로 적으면 후보 조건·순위 기준·종목 수까지 채운 초안이 만들어집니다. 지표 이름이나 수치를 미리 알고 있어야 하는 것은 아닙니다."
          actions={
            <>
              <Link className="btn primary" to={routes.home}>
                전략 만들기
              </Link>
              <Link className="btn" to={routes.ranking}>
                공개 전략 둘러보기
              </Link>
            </>
          }
        />
      )}
      {strategies.isSuccess && strategies.data.length > 0 && (
        <div className="card">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>전략</th>
                  <th>상태</th>
                  <th className="r">백테스트 수익률</th>
                  <th className="r">최대 낙폭</th>
                  <th>최근 수정</th>
                  <th style={{ textAlign: 'right' }}>다음 할 일</th>
                </tr>
              </thead>
              <tbody>
                {strategies.data.map((strategy) => (
                  <StrategyRow
                    key={strategy.id}
                    strategy={strategy}
                    onOpen={() => navigate(to.strategyDetail(strategy.id))}
                    onBacktest={() => {
                      const entry: BacktestEntryState = { strategyId: strategy.id }
                      navigate(routes.backtest, { state: entry })
                    }}
                  />
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  )
}

type StrategyRowProps = {
  strategy: Strategy
  onOpen: () => void
  onBacktest: () => void
}

/* 행 전체가 클릭 영역이지만 키보드 사용자는 이름 링크로 들어간다. 행 안의 버튼은 행 클릭으로 번지지 않는다. */
function StrategyRow({ strategy, onOpen, onBacktest }: StrategyRowProps) {
  const handleRowClick = (event: MouseEvent<HTMLTableRowElement>) => {
    if ((event.target as HTMLElement).closest('a, button')) {
      return
    }
    onOpen()
  }

  return (
    <tr className="click" onClick={handleRowClick}>
      <td className="strategy-name-cell">
        <b>
          <Link to={to.strategyDetail(strategy.id)}>{strategy.name}</Link>
        </b>
        <small>{strategy.description || summarizeDsl(strategy.dsl)} · v1</small>
      </td>
      <td>
        <span className="chip mute">초안</span>
      </td>
      <td className="r muted-cell">—</td>
      <td className="r muted-cell">—</td>
      <td className="muted-cell">{dateFormat.format(strategy.createdAt)}</td>
      <td style={{ textAlign: 'right' }}>
        <button className="rank-action" type="button" onClick={onBacktest}>
          백테스트 실행
        </button>
      </td>
    </tr>
  )
}
