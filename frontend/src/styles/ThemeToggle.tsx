import { useTheme } from './theme'

/*
 * 두 아이콘을 모두 그려두고 base.css 가 현재 테마에 맞는 하나만 보여준다.
 * 아이콘을 조건부로 렌더링하면 전환할 때마다 SVG 가 교체돼 깜빡인다.
 */
export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()

  return (
    <button
      className="theme-btn"
      type="button"
      onClick={toggleTheme}
      title="라이트 / 다크 모드 전환"
      aria-label={theme === 'light' ? '다크 모드로 전환' : '라이트 모드로 전환'}
    >
      <svg className="moon" viewBox="0 0 24 24" strokeLinecap="round" strokeLinejoin="round">
        <path d="M20 14.5A8.5 8.5 0 1 1 9.5 4a7 7 0 0 0 10.5 10.5z" />
      </svg>
      <svg className="sun" viewBox="0 0 24 24" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M18.4 5.6L17 7M7 17l-1.4 1.4" />
      </svg>
    </button>
  )
}
