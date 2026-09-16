import { isApiError } from '../api/error.ts'
import { errorCodes } from '../api/errorCodes.ts'

/*
 * 제출 실패를 화면 문장으로 바꾼다. 코드마다 사용자가 할 일이 다르다. 마감과 횟수 초과는
 * 다시 눌러도 같은 결과라 버튼을 닫고, 팩터 기간 초과는 전략을 고쳐야 한다.
 */
export type ContestSubmitErrorView = {
  title: string
  description: string
  /* 다시 눌러 볼 여지가 없는 실패는 제출 버튼을 잠근다. */
  retryable: boolean
}

export function describeSubmitError(error: unknown): ContestSubmitErrorView {
  const code = isApiError(error) ? error.code : null

  if (code === errorCodes.contestSubmissionClosed) {
    return {
      title: '지금은 제출 기간이 아닙니다',
      description: '마감됐거나 아직 시작하지 않은 회차입니다. 화면을 새로 고쳐 회차 상태를 확인해 주세요.',
      retryable: false,
    }
  }
  if (code === errorCodes.contestSubmissionLimitExceeded) {
    return {
      title: '제출 횟수를 모두 썼습니다',
      description: '이번 회차에는 더 이상 전략을 바꿀 수 없습니다. 마지막으로 제출한 전략으로 채점됩니다.',
      retryable: false,
    }
  }
  if (code === errorCodes.contestFactorPeriodTooLong) {
    return {
      title: '팩터 기간이 상한을 넘습니다',
      description:
        '대회 데이터의 워밍업 구간보다 긴 기간은 값을 만들 수 없습니다. 아래 상한 이하로 전략을 고친 뒤 다시 제출해 주세요.',
      retryable: false,
    }
  }
  if (code === errorCodes.strategyNotFound) {
    return {
      title: '전략을 찾을 수 없습니다',
      description: '삭제된 전략이거나 접근할 수 없는 전략입니다. 목록에서 다시 골라 주세요.',
      retryable: true,
    }
  }
  if (code === errorCodes.contestRoundNotFound) {
    return {
      title: '회차를 찾을 수 없습니다',
      description: '사라진 회차입니다. 대회 목록에서 다시 들어와 주세요.',
      retryable: false,
    }
  }
  return {
    title: '제출하지 못했습니다',
    description: `${isApiError(error) ? error.message : '서버에 연결할 수 없습니다.'} 잠시 후 다시 시도해 주세요.`,
    retryable: true,
  }
}

/* 결과·데이터셋 조회는 종료 전이면 409 다. 오류가 아니라 아직 공개 전이라는 뜻이다. */
export function isResultNotPublished(error: unknown): boolean {
  return isApiError(error) && error.code === errorCodes.contestResultNotPublished
}

/* 제출하지 않은 회차의 내 결과는 404 다. */
export function isEntryNotFound(error: unknown): boolean {
  return isApiError(error) && error.code === errorCodes.contestEntryNotFound
}
