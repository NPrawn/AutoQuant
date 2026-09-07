/* backend/api 의 MemberResponse 와 같은 모양. 화면은 이 타입을 직접 쓰지 않고 Member 로 바꿔 쓴다. */
export type MemberResponse = {
  memberId: string
  displayName: string
  emailAddress: string
  memberStatus: string
  lastLoginAt: string | null
}

export type AuthorizeUrlResponse = {
  authorizeUrl: string
}

/*
 * /members/me 조회 결과. 401 은 실패가 아니라 "로그인 안 됨"이라는 답이므로 회원이
 * 없는 이유(오류 코드)를 함께 들고 있어 화면이 만료 안내를 할 수 있다.
 */
export type CurrentMemberResponse = {
  member: MemberResponse | null
  unauthorizedCode: string | null
}

/* 화면이 쓰는 회원 모델. 표시에 필요한 것만 남긴다. */
export type Member = {
  id: string
  displayName: string
  emailAddress: string
}

export type CurrentMember = {
  member: Member | null
  unauthorizedCode: string | null
}

export function toMember(response: MemberResponse): Member {
  return {
    id: response.memberId,
    displayName: response.displayName,
    emailAddress: response.emailAddress,
  }
}

export function toCurrentMember(response: CurrentMemberResponse): CurrentMember {
  return {
    member: response.member ? toMember(response.member) : null,
    unauthorizedCode: response.unauthorizedCode,
  }
}
