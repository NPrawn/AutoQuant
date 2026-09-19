import type { ReactNode } from 'react'

import { isApiError } from '../api/error'

/*
 * 목업의 .state-block 을 그대로 쓰는 빈 상태와 오류 상태. 화면마다 문구만 다르고
 * 구조는 같아서 공통으로 둔다. 로딩은 LoadingState 가 맡는다.
 */
type StateBlockProps = {
  title: string
  description?: ReactNode
  icon?: ReactNode
  actions?: ReactNode
  detail?: ReactNode
  variant?: 'empty' | 'error'
}

function StateBlock({ title, description, icon, actions, detail, variant = 'empty' }: StateBlockProps) {
  return (
    <div className={variant === 'error' ? 'state-block is-error' : 'state-block'} role={variant === 'error' ? 'alert' : undefined}>
      {icon && <div className="state-icon" aria-hidden="true">{icon}</div>}
      <h3>{title}</h3>
      {description && <p>{description}</p>}
      {detail}
      {actions && <div className="state-acts">{actions}</div>}
    </div>
  )
}

type EmptyStateProps = Omit<StateBlockProps, 'variant'>

export function EmptyState(props: EmptyStateProps) {
  return <StateBlock {...props} variant="empty" icon={props.icon ?? <EmptyIcon />} />
}

export type ErrorDetailRow = {
  label: string
  value: string
}

type ErrorStateProps = {
  title?: string
  description?: ReactNode
  /* 있으면 서버가 보낸 문장을 설명으로 쓰고 코드를 개발자용 상세에 넣는다. */
  error?: unknown
  /* 목업 24·27 의 .state-detail. 무엇이 잘못됐는지 항목별로 보일 때 쓴다. */
  details?: ErrorDetailRow[]
  onRetry?: () => void
  /* 재시도 버튼 글자. 새로고침처럼 동작이 다르면 그렇게 적는다. */
  retryLabel?: string
  actions?: ReactNode
}

const defaultErrorTitle = '문제가 발생했습니다'
const defaultErrorDescription = '잠시 후 다시 시도해 주세요. 계속되면 관리자에게 알려 주세요.'

export function ErrorState({ title = defaultErrorTitle, description, error, details, onRetry, retryLabel = '다시 시도', actions }: ErrorStateProps) {
  const apiError = isApiError(error) ? error : null

  return (
    <StateBlock
      variant="error"
      icon={<ErrorIcon />}
      title={title}
      description={description ?? apiError?.message ?? defaultErrorDescription}
      detail={
        <>
          {details && details.length > 0 && (
            <div className="state-detail">
              {details.map((row, index) => (
                <div key={`${row.label}-${index}`}>
                  <span>{row.label}</span>
                  <strong>{row.value}</strong>
                </div>
              ))}
            </div>
          )}
          {apiError?.code && (
            <details className="err-code">
              <summary>개발자용 상세</summary>
              <div>
                {apiError.code} · {apiError.status}
                {apiError.response?.path && ` · ${apiError.response.path}`}
              </div>
            </details>
          )}
        </>
      }
      actions={
        (onRetry || actions) && (
          <>
            {onRetry && (
              <button className="btn primary" type="button" onClick={onRetry}>
                {retryLabel}
              </button>
            )}
            {actions}
          </>
        )
      }
    />
  )
}

function EmptyIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <rect x="4" y="5" width="16" height="14" rx="2" />
      <path d="M4 10h16M9 14h6" />
    </svg>
  )
}

function ErrorIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8v5M12 16h.01" />
    </svg>
  )
}
