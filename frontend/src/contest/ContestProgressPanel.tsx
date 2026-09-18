import { progressRatio } from './labels'
import type { ContestProgressResponse } from './types'

/*
 * 목업 32. 5단계 표시 대신 끝난 백테스트 비율을 보여 준다 (대회 기능 설계 9절).
 * 제출마다 데이터셋 4벌을 돌리므로 전체 수는 참가 수 × 4다.
 */
export function ContestProgressPanel({ progress }: { progress: ContestProgressResponse }) {
  const ratio = progressRatio(progress.finishedBacktests, progress.totalBacktests)
  const percent = Math.round(ratio * 100)

  return (
    <div className="card pad">
      <div className="sec-head">
        <div>
          <div className="sec-title">진행 상황</div>
          <div className="sec-sub">제출마다 일반·상승·하락·횡보 4벌을 돌립니다</div>
        </div>
        <span className="chip mute">{percent}%</span>
      </div>
      <div className="pct">
        <span>백테스트</span>
        <div className="pct-track">
          <i style={{ width: `${percent}%` }} />
        </div>
        <span className="pct-val">
          {progress.finishedBacktests} / {progress.totalBacktests}
        </span>
      </div>
      <p className="sec-sub" style={{ marginTop: 12 }}>
        모두 끝나면 점수를 계산하고 순위를 공개합니다. 이 화면은 자동으로 갱신됩니다.
      </p>
    </div>
  )
}
