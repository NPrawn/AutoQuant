/*
 * 홈에서 빌더로 넘어갈 때 함께 보내는 값. URL 에 붙이면 문장이 길어지고 주소가 공유되면
 * 남의 입력이 노출되므로 라우터 state 로 전달한다. 새로고침하면 사라지는 것이 의도다.
 */
export type BuilderEntryState = {
  prompt: string
}

export function readBuilderEntry(state: unknown): BuilderEntryState | null {
  if (typeof state !== 'object' || state === null) {
    return null
  }
  const prompt = (state as { prompt?: unknown }).prompt
  if (typeof prompt !== 'string' || prompt.trim() === '') {
    return null
  }
  return { prompt }
}
