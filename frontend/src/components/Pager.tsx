type PagerProps = {
  page: number
  totalPages: number
  disabled?: boolean
  onPageChange: (page: number) => void
}

/* 표 아래 페이지 이동. 목업에는 표 페이지가 없어 extensions.css 의 .pager 를 쓴다. 한 페이지면 그리지 않는다. */
export function Pager({ page, totalPages, disabled = false, onPageChange }: PagerProps) {
  if (totalPages <= 1) {
    return null
  }
  return (
    <div className="pager">
      <button className="btn ghost sm" type="button" disabled={disabled || page === 0} onClick={() => onPageChange(page - 1)}>
        이전
      </button>
      <span className="mono">
        {page + 1} / {totalPages}
      </span>
      <button
        className="btn ghost sm"
        type="button"
        disabled={disabled || page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        다음
      </button>
    </div>
  )
}
