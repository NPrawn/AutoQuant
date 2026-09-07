/* 인증 연동 전까지 상단바와 계정 메뉴에 보일 회원. API 가 붙으면 /members/me 응답으로 바뀐다. */
export const demoMember = {
  displayName: '하윤',
  emailAddress: 'hayoon@strategylab.kr',
} as const

/* 로그아웃 확인 모달이 보여 주는 운용 상태. 모의투자 API 가 붙으면 조회 결과로 바뀐다. */
export const demoSession = {
  runningSimulations: 2,
  hasUnsavedEdits: false,
} as const
