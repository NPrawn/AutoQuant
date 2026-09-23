import { useState } from 'react'
import { Link, useParams } from 'react-router'

import { routes, to } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { ContestFactorCapsCard, ContestRulesCard } from '../contest/ContestRulesCard'
import { ContestSubmitDialog } from '../contest/ContestSubmitDialog'
import { ContestSubmitPanel } from '../contest/ContestSubmitPanel'
import { useNow } from '../contest/clock'
import { contestStatusLabels, formatDateTime, formatRemaining } from '../contest/labels'
import { useContest, useSubmitContestEntry } from '../contest/queries'
import { describeSubmitError } from '../contest/submitErrorMessage'
import { pickerOptions } from '../strategy/lineage'
import { useStrategies } from '../strategy/queries'

/*
 * 목업 31 과 36. 회차 규칙을 보여 주고 전략 하나를 제출한다. 제출과 교체는 같은 요청이라
 * 버튼도 하나다. 목업의 연습 성적·연습 점수 영역은 채점용 데이터만 두기로 해서 뺐다.
 */
export function ContestDetailPage() {
  const { contestId } = useParams<{ contestId: string }>()
  const contest = useContest(contestId)
  const now = useNow()
  const strategies = useStrategies()
  const submit = useSubmitContestEntry(contestId ?? '')

  const [strategyId, setStrategyId] = useState('')
  const [confirmOpen, setConfirmOpen] = useState(false)

  const round = contest.data ?? null
  /* 셀렉트에는 최신 버전만 올린다. 이미 낸 전략이 옛 버전이면 그것도 올려 교체 전에 무엇을 냈는지 보이게 한다. */
  const submitted = round?.mySubmission ? String(round.mySubmission.strategyId) : ''
  const available = pickerOptions(strategies.data ?? [], submitted === '' ? null : submitted)

  /* 전략 목록과 회차가 모두 온 뒤에 고른다. 목록이 먼저 오면 제출한 전략을 모른 채 첫 전략을 골라 버린다. */
  if (strategyId === '' && available.length > 0 && contest.isSuccess) {
    setStrategyId(available.some((strategy) => strategy.id === submitted) ? submitted : available[0].id)
  }

  const selected = available.find((strategy) => strategy.id === strategyId) ?? null

  if (contest.isPending) {
    return (
      <section className="view active">
        <LoadingState label="대회 회차를 불러오는 중…" />
      </section>
    )
  }
  if (contest.isError || round === null) {
    return (
      <section className="view active">
        <Link className="back" to={routes.contests}>
          대회 목록
        </Link>
        <ErrorState title="회차를 불러오지 못했습니다" error={contest.error} onRetry={() => contest.refetch()} />
      </section>
    )
  }

  const open = round.status === 'OPEN'
  const remaining = formatRemaining(round.submissionEndsAt, now)
  const errorView = submit.isError ? describeSubmitError(submit.error) : null

  return (
    <section className="view active" id="view-contest-detail">
      <Link className="back" to={routes.contests}>
        대회 목록
      </Link>

      <div className="page-head">
        <div>
          <span className="eyebrow">{contestStatusLabels[round.status]}</span>
          <h1>{round.title}</h1>
        </div>
        <div className="detail-actions">
          <span className={open ? 'stream-status live' : 'stream-status'} role="status">
            <i aria-hidden="true" />
            <span>{open ? '제출 가능' : contestStatusLabels[round.status]}</span>
            <em className="mono">{remaining ?? formatDateTime(round.submissionEndsAt)}</em>
          </span>
          {(round.status === 'SCORING' || round.status === 'FINISHED') && (
            <Link className="btn primary" to={to.contestResult(round.roundId)}>
              {round.status === 'SCORING' ? '채점 현황' : '결과 보기'}
            </Link>
          )}
        </div>
      </div>

      <div className="two" style={{ marginBottom: 16 }}>
        <ContestRulesCard round={round} />
        <ContestFactorCapsCard round={round} />
      </div>

      <div className="sec-head" style={{ margin: '24px 0 12px' }}>
        <div>
          <div className="sec-title">제출</div>
        </div>
        <span className="chip mute">제출 {round.entryCount}</span>
      </div>

      {errorView && (
        <div style={{ marginBottom: 16 }}>
          <ErrorState
            title={errorView.title}
            description={errorView.description}
            error={submit.error}
            actions={
              <button className="btn" type="button" onClick={() => submit.reset()}>
                닫기
              </button>
            }
          />
        </div>
      )}

      {!open && (
        <EmptyState
          title={round.status === 'SCHEDULED' ? '아직 제출 기간이 아닙니다' : '제출이 마감됐습니다'}
          description={
            round.status === 'SCHEDULED'
              ? `${formatDateTime(round.submissionStartsAt)}부터 전략을 제출할 수 있습니다.`
              : '마감된 회차입니다. 채점이 끝나면 결과를 볼 수 있습니다.'
          }
        />
      )}

      {open && strategies.isPending && <LoadingState label="전략을 불러오는 중…" />}
      {open && strategies.isError && (
        <ErrorState title="전략을 불러오지 못했습니다" error={strategies.error} onRetry={() => strategies.refetch()} />
      )}
      {open && strategies.isSuccess && available.length === 0 && (
        <EmptyState
          title="제출할 전략이 없습니다"
          description="전략을 먼저 만들면 이 회차에 제출할 수 있습니다."
          actions={
            <Link className="btn primary" to={routes.strategyNew}>
              전략 만들기
            </Link>
          }
        />
      )}
      {open && available.length > 0 && (
        <ContestSubmitPanel
          strategies={available}
          strategyId={strategyId}
          submission={round.mySubmission}
          pending={submit.isPending}
          disabled={errorView !== null && !errorView.retryable}
          onChange={setStrategyId}
          onSubmit={() => {
            submit.reset()
            setConfirmOpen(true)
          }}
        />
      )}

      <ContestSubmitDialog
        open={confirmOpen}
        round={round}
        strategy={selected}
        pending={submit.isPending}
        onConfirm={() =>
          submit.mutate(
            { strategyId: Number(strategyId) },
            { onSettled: () => setConfirmOpen(false) },
          )
        }
        onCancel={() => {
          if (!submit.isPending) setConfirmOpen(false)
        }}
      />
    </section>
  )
}
