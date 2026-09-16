import { useEffect, useState } from 'react'

/*
 * 마감까지 남은 시간을 그리는 화면이 쓰는 시계. 표시가 분 단위라 1분마다만 다시 읽는다.
 * 렌더 중에 Date.now() 를 부르면 같은 입력에 값이 달라져 렌더가 순수하지 않다.
 */
export function useNow(intervalMs = 60_000): number {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), intervalMs)
    return () => window.clearInterval(timer)
  }, [intervalMs])

  return now
}
