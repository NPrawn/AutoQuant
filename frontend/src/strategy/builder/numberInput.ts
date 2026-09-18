import type { WheelEvent } from 'react'

/*
 * 숫자 입력에 포커스를 둔 채 페이지를 스크롤하면 브라우저가 값을 조용히 바꾼다.
 * 전략 파라미터에서는 치명적이라 휠이 닿으면 포커스를 뺀다.
 */
export function blurOnWheel(event: WheelEvent<HTMLInputElement>): void {
  event.currentTarget.blur()
}
