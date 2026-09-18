type PagerProps = {
  page: number
  totalPages: number
  disabled?: boolean
  /* 어느 표의 페이저인지. 한 화면에 표가 둘 있을 때 보조기기가 구분한다. */
  label?: string
  onPageChange: (page: number) => void
}

/*
 * 표 아래 페이지 이동. 목업에는 표 페이지가 없어 extensions.css 의 .pager 를 쓴다.
 * 한 페이지여도 자리를 비워 두고 숨긴다. 조건을 바꿔 페이지 수가 1 이 되는 순간 아래 내용이 튀지 않는다.
 */
export function Pager({ page, totalPages, disabled = false, label = '페이지 이동', onPageChange }: PagerProps) {
  if (totalPages === 0) {
    return null
  }
  const single = totalPages <= 1
  return (
    <nav className="pager" aria-label={label} aria-hidden={single}>
      <button className="btn ghost sm" type="button" disabled={single || disabled || page === 0} onClick={() => onPageChange(page - 1)}>
        이전
      </button>
      <span className="mono" aria-live="polite">
        {page + 1} / {totalPages}
      </span>
      <button
        className="btn ghost sm"
        type="button"
        disabled={single || disabled || page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        다음
      </button>
    </nav>
  )
}
