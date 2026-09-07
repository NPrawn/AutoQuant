/*
 * 목업의 .bt-load .spin 을 그대로 쓴다. 이름은 백테스트에서 왔지만 목업 전체에서
 * 유일한 로딩 표현이라 공통으로 재사용한다.
 */
type LoadingStateProps = {
  label?: string
}

export function LoadingState({ label = '불러오는 중…' }: LoadingStateProps) {
  return (
    <div className="bt-load on" role="status" aria-live="polite">
      <div className="spin" aria-hidden="true" />
      <div>{label}</div>
    </div>
  )
}
