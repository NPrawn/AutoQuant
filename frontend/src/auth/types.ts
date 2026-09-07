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

/* 화면이 쓰는 회원 모델. 표시에 필요한 것만 남긴다. */
export type Member = {
  id: string
  displayName: string
  emailAddress: string
}

export function toMember(response: MemberResponse): Member {
  return {
    id: response.memberId,
    displayName: response.displayName,
    emailAddress: response.emailAddress,
  }
}
