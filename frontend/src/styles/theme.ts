import { useCallback, useEffect, useState } from 'react'

/*
 * 테마는 문서 루트의 data-theme 속성으로 결정된다. tokens.css 가 이 속성만 보고
 * 팔레트를 바꾸므로 컴포넌트는 색을 직접 다루지 않는다.
 */
const STORAGE_KEY = 'aq-theme'
const LIGHT_ATTRIBUTE = 'light'

export type Theme = 'dark' | 'light'

/* 다크가 기본이다. 라이트일 때만 속성이 붙는다. */
function readAppliedTheme(): Theme {
  return document.documentElement.getAttribute('data-theme') === LIGHT_ATTRIBUTE ? 'light' : 'dark'
}

function applyTheme(theme: Theme): void {
  if (theme === 'light') {
    document.documentElement.setAttribute('data-theme', LIGHT_ATTRIBUTE)
  } else {
    document.documentElement.removeAttribute('data-theme')
  }
}

/* 시크릿 모드나 저장소를 막은 브라우저에서는 선택을 기억하지 못해도 화면은 동작해야 한다. */
function persistTheme(theme: Theme): void {
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    /* 기억하지 못할 뿐이므로 무시한다 */
  }
}

/*
 * 첫 페인트 전 테마 복원은 index.html 의 인라인 스크립트가 이미 처리했다.
 * 이 훅은 그 결과를 초기값으로 받아 전환만 담당한다.
 */
export function useTheme(): { theme: Theme; toggleTheme: () => void } {
  const [theme, setTheme] = useState<Theme>(readAppliedTheme)

  useEffect(() => {
    applyTheme(theme)
    persistTheme(theme)
  }, [theme])

  const toggleTheme = useCallback(() => {
    setTheme((current) => (current === 'light' ? 'dark' : 'light'))
  }, [])

  return { theme, toggleTheme }
}
