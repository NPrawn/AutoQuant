import { Link, useLocation, useParams } from 'react-router'

import { isApiError } from '../api/error'
import { routes } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/StateBlock'
import { readBuilderEntry } from '../strategy/builderEntry'
import { StrategyEditor } from '../strategy/builder/StrategyEditor'
import { createEmptyDraft, draftFromDsl } from '../strategy/draft'
import { useStrategy } from '../strategy/queries'

/*
 * 빌더 진입점. 새 전략(/strategies/new)은 빈 초안이나 홈에서 온 문장으로, 수정(/strategies/:id/edit)은
 * 저장된 전략을 불러와 초안으로 시작한다. 편집 본체는 StrategyEditor 가 맡고, 불러온 전략이
 * 바뀌면 key 로 다시 마운트해 초안을 새로 만든다.
 */
export function StrategyBuilderPage() {
  const { strategyId } = useParams<{ strategyId?: string }>()
  const location = useLocation()
  const strategy = useStrategy(strategyId)

  if (strategyId === undefined) {
    return <StrategyEditor initialDraft={createEmptyDraft()} entry={readBuilderEntry(location.state)} mode="new" />
  }
  if (strategy.isPending) {
    return <LoadingState label="전략을 불러오는 중…" />
  }
  if (strategy.isError) {
    const notFound = isApiError(strategy.error) && strategy.error.status === 404
    return (
      <ErrorState
        title={notFound ? '수정할 전략을 찾을 수 없습니다' : '전략을 불러오지 못했습니다'}
        error={strategy.error}
        onRetry={notFound ? undefined : () => strategy.refetch()}
        actions={
          <Link className="btn" to={routes.strategies}>
            목록으로
          </Link>
        }
      />
    )
  }

  const { data } = strategy
  return (
    <StrategyEditor
      key={data.id}
      initialDraft={draftFromDsl(data.dsl, { name: data.name, description: data.description })}
      entry={null}
      mode="edit"
    />
  )
}
