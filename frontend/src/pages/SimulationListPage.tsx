import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'

import { routes, to } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { Pager } from '../components/Pager'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { useAssetLookup } from '../simulation/assetLookup'
import { describeLaunchError } from '../simulation/errorMessage'
import { useLaunchSimulation, useSimulationRuns } from '../simulation/queries'
import { SimulationCard } from '../simulation/SimulationCard'
import { readSimulationEntry } from '../simulation/simulationEntry'
import { SimulationStartDialog } from '../simulation/SimulationStartDialog'
import { SimulationStartPanel } from '../simulation/SimulationStartPanel'
import { createStartForm, setStrategyId, toCreateRequest, validateStartForm } from '../simulation/startForm'
import { useStrategies } from '../strategy/queries'

/*
 * 모의투자 목록(목업 16, 17). 위에서 새 운용을 시작하고 아래에서 실행 카드를 본다.
 * 시작은 확인 모달을 거쳐 생성과 시작을 이어서 부르고, 성공하면 실시간 화면으로 간다.
 */
export function SimulationListPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const entry = readSimulationEntry(location.state)

  const strategies = useStrategies()
  const lookup = useAssetLookup()
  const [page, setPage] = useState(0)
  const runs = useSimulationRuns(page)
  const launch = useLaunchSimulation()

  const [form, setForm] = useState(() => createStartForm(entry?.strategyId ?? ''))
  const [showIssues, setShowIssues] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)

  /* 전략 목록이 도착하면 선택이 비어 있거나 목록에 없을 때 첫 전략을 채운다. 진입 상태가 지워진 전략을 가리킬 수 있다. */
  if (strategies.data !== undefined && strategies.data.length > 0 && !strategies.data.some((strategy) => strategy.id === form.strategyId)) {
    setForm(setStrategyId(form, strategies.data[0].id))
  }

  const selectedStrategy = strategies.data?.find((strategy) => strategy.id === form.strategyId) ?? null
  const strategyNames = new Map((strategies.data ?? []).map((strategy) => [strategy.id, strategy.name]))

  const requestStart = () => {
    setShowIssues(true)
    if (validateStartForm(form).length > 0) {
      return
    }
    launch.reset()
    setConfirmOpen(true)
  }

  const confirmStart = () => {
    launch.mutate(toCreateRequest(form), {
      onSuccess: (started) => navigate(to.simulationLive(started.simulationId)),
      onError: () => setConfirmOpen(false),
    })
  }

  return (
    <section className="view active" id="view-simulation">
      <div className="page-head">
        <div>
          <span className="eyebrow">대회 제출 전 리허설</span>
          <h1>모의투자</h1>
          <p className="sub">
            실제 시세로 전략이 어떻게 움직이는지 확인하는 리허설입니다. 순위는 대회 결과로만 매겨지므로 이 성적은 랭킹에 반영되지 않습니다.
          </p>
        </div>
      </div>

      {strategies.isPending && <LoadingState label="전략을 불러오는 중…" />}
      {strategies.isError && (
        <ErrorState title="전략을 불러오지 못했습니다" error={strategies.error} onRetry={() => strategies.refetch()} />
      )}
      {strategies.isSuccess && strategies.data.length === 0 && (
        <EmptyState
          title="운용할 전략이 없습니다"
          description="전략을 먼저 만들면 실제 시세로 모의운용할 수 있습니다."
          actions={
            <Link className="btn primary" to={routes.home}>
              전략 만들기
            </Link>
          }
        />
      )}
      {strategies.isSuccess && strategies.data.length > 0 && (
        <SimulationStartPanel
          strategies={strategies.data}
          form={form}
          pending={launch.isPending}
          showIssues={showIssues}
          onChange={setForm}
          onSubmit={requestStart}
        />
      )}

      {launch.isError && <LaunchError error={launch.error} onDismiss={() => launch.reset()} />}

      <div className="sim-overview-title">
        <div>
          <h2>운용 중인 전략</h2>
        </div>
        <span>카드를 선택하면 실시간 운용 화면으로 이동합니다</span>
      </div>

      {runs.isPending && <LoadingState label="모의투자 목록을 불러오는 중…" />}
      {runs.isError && <ErrorState title="모의투자 목록을 불러오지 못했습니다" error={runs.error} onRetry={() => runs.refetch()} />}
      {runs.isSuccess && runs.data.totalElements === 0 && (
        <EmptyState
          title="아직 모의투자가 없습니다"
          description="위에서 전략과 초기 모의자산을 고르고 시작하면 실제 시세로 운용이 시작됩니다."
        />
      )}
      {runs.isSuccess && runs.data.totalElements > 0 && (
        <>
          <div className="simg">
            {runs.data.content.map((run) => (
              <SimulationCard
                key={run.id}
                run={run}
                strategyName={strategyNames.get(run.strategyId) ?? `전략 #${run.strategyId}`}
                lookup={lookup}
                onOpen={() => navigate(to.simulationLive(run.id))}
              />
            ))}
          </div>
          <Pager page={page} totalPages={runs.data.totalPages} disabled={runs.isPlaceholderData} onPageChange={setPage} />
        </>
      )}

      <SimulationStartDialog
        open={confirmOpen}
        strategy={selectedStrategy}
        form={form}
        pending={launch.isPending}
        onConfirm={confirmStart}
        onCancel={() => {
          if (!launch.isPending) setConfirmOpen(false)
        }}
      />
    </section>
  )
}

/* 시작 실패. 400 은 항목별로 풀어 보이고 입력은 그대로 남긴다. */
function LaunchError({ error, onDismiss }: { error: unknown; onDismiss: () => void }) {
  const view = describeLaunchError(error)
  return (
    <div style={{ margin: '16px 0' }}>
      <ErrorState
        title={view.title}
        description={view.description}
        details={view.details}
        error={error}
        actions={
          <button className="btn" type="button" onClick={onDismiss}>
            닫기
          </button>
        }
      />
    </div>
  )
}
