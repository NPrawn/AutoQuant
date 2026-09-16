import { datasetKindLabels, formatDate, formatPercent } from './labels'
import { parseSourceWeeks } from './types'
import type { ContestDatasetItemResponse, ContestDatasetOutcomeResponse, MarketDatasetKind } from './types'

/* 목업의 .regime 은 상승·하락에만 색을 준다. 일반장과 횡보장은 기본 색이다. */
function regimeClass(kind: MarketDatasetKind): string {
  if (kind === 'BULL') {
    return 'regime bull'
  }
  if (kind === 'BEAR') {
    return 'regime bear'
  }
  return 'regime'
}

function signClass(value: number | null): string {
  if (value === null) {
    return 'muted-cell'
  }
  return value >= 0 ? 'updown up' : 'updown down'
}

/* 목업 33·35 의 국면별 성적. 백테스트가 실패한 데이터셋은 값이 비어 온다. */
export function ContestOutcomeGrid({ outcomes }: { outcomes: ContestDatasetOutcomeResponse[] }) {
  return (
    <div className="regime-grid">
      {outcomes.map((outcome) => (
        <div className={regimeClass(outcome.kind)} key={outcome.kind}>
          <div className="regime-head">
            <i />
            <b>{datasetKindLabels[outcome.kind]}</b>
            <em>{outcome.cumulativeReturn === null ? '실행 실패' : `체결 ${outcome.tradeCount ?? 0}`}</em>
          </div>
          <dl>
            <dt>구간 수익률</dt>
            <dd className={signClass(outcome.cumulativeReturn)}>{formatPercent(outcome.cumulativeReturn)}</dd>
            <dt>시장 수익률</dt>
            <dd className={signClass(outcome.marketReturn)}>{formatPercent(outcome.marketReturn)}</dd>
            <dt>구간 최대 낙폭</dt>
            <dd className="dd-val">{formatPercent(outcome.maximumDrawdown)}</dd>
            <dt>평균 투자 비중</dt>
            <dd>{formatPercent(outcome.averageExposure)}</dd>
          </dl>
        </div>
      ))}
    </div>
  )
}

/*
 * 목업에 없는 영역. 채점용 데이터가 어떻게 만들어졌는지 마감 뒤에 공개한다 (ADR-0010).
 * 뽑은 주 목록은 길어서 개수만 적고 원본 구간과 시드로 재현 경로를 남긴다.
 */
export function ContestDatasetTable({ datasets }: { datasets: ContestDatasetItemResponse[] }) {
  return (
    <div className="card">
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>장세</th>
              <th>평가 기간</th>
              <th className="r">시장 수익률</th>
              <th>원본 구간</th>
              <th className="r">뽑은 주</th>
              <th className="r">시드</th>
              <th>생성기</th>
            </tr>
          </thead>
          <tbody>
            {datasets.map((dataset) => (
              <tr key={dataset.kind}>
                <td>{datasetKindLabels[dataset.kind]}</td>
                <td className="mono">
                  {formatDate(dataset.periodStart)} ~ {formatDate(dataset.periodEnd)}
                </td>
                <td className={`r ${signClass(dataset.evaluationReturn)}`}>{formatPercent(dataset.evaluationReturn)}</td>
                <td className="mono muted-cell">
                  {formatDate(dataset.sourceWindowStart)} ~ {formatDate(dataset.sourceWindowEnd)}
                </td>
                <td className="r mono">{parseSourceWeeks(dataset.sourceWeeks).length}주</td>
                <td className="r mono">{dataset.seed}</td>
                <td className="muted-cell">{dataset.generatorVersion}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
