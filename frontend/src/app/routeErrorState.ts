import { useSyncExternalStore } from 'react'

/*
 * 오류 화면이 떠 있는지. 상단바는 라우트 handle 로 제목을 읽는데, 오류 상태에서도 원래 화면이
 * 매치돼 제목이 그대로 남는다. RouteErrorPage 가 켜고 끄며 상단바가 구독한다. 전역 상태 라이브러리는 두지 않는다.
 */
let shown = false
const listeners = new Set<() => void>()

export function setRouteErrorShown(next: boolean): void {
  if (shown === next) {
    return
  }
  shown = next
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function useRouteErrorShown(): boolean {
  return useSyncExternalStore(subscribe, () => shown, () => false)
}
