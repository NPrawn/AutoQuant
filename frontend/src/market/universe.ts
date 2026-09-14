import { useChartAssets } from './queries'

/*
 * 후보군 크기. 화면 여러 곳이 "상위 N" 이라고 말하는데 실제 N 은 등록된 종목 수다.
 * 서버는 등록된 Upbit KRW 거래지원 종목을 전부 후보로 쓰므로 목록 길이가 곧 답이다.
 * 상수로 세 군데에 적어 두면 종목을 더하거나 뺄 때 화면이 거짓말을 한다.
 */

/* 목록을 아직 못 받았을 때 쓰는 값. 운영 등록 수와 같게 둔다. */
export const defaultUniverseSize = 30

export function useUniverseSize(): number {
  const assets = useChartAssets()
  return assets.data?.length ?? defaultUniverseSize
}
