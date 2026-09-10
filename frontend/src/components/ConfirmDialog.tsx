import type { MouseEvent, ReactNode } from 'react'
import { useEffect, useId, useRef } from 'react'

/*
 * 되돌릴 수 없는 동작 앞에 한 번 묻는 모달. 네이티브 dialog 를 showModal 로 열어 포커스 트랩,
 * Esc 닫기, 바깥 요소 비활성화를 브라우저에 맡기고 겉모습은 목업의 .mbd 와 .modal 을 쓴다.
 */
type ConfirmDialogProps = {
  open: boolean
  title: string
  description: ReactNode
  detail?: ReactNode
  confirmLabel: string
  cancelLabel?: string
  /* 삭제처럼 위험한 확정은 오류 색으로 구분한다. 색 계약상 빨강은 상승이라 쓰지 않는다. */
  danger?: boolean
  pending?: boolean
  /* 동의 체크처럼 확정 전에 채워야 하는 조건이 있을 때 확정 버튼만 막는다. */
  confirmDisabled?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({
  open,
  title,
  description,
  detail,
  confirmLabel,
  cancelLabel = '취소',
  danger = false,
  pending = false,
  confirmDisabled = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) {
      return
    }
    if (open && !dialog.open) {
      dialog.showModal()
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open])

  /* 스크림은 dialog 요소 자체가 화면을 덮어 만든다. 카드 바깥을 누르면 target 이 dialog 다. */
  const handleBackdropClick = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === dialogRef.current && !pending) {
      onCancel()
    }
  }

  return (
    <dialog ref={dialogRef} className="mbd on" aria-labelledby={titleId} onCancel={onCancel} onClose={onCancel} onClick={handleBackdropClick}>
      <div className="modal">
        <h3 id={titleId}>{title}</h3>
        <p className="s">{description}</p>
        {detail}
        <div className="macts">
          <button className="btn" type="button" onClick={onCancel} disabled={pending}>
            {cancelLabel}
          </button>
          <button
            className={danger ? 'btn primary danger' : 'btn primary'}
            type="button"
            onClick={onConfirm}
            disabled={pending || confirmDisabled}
            aria-busy={pending}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </dialog>
  )
}
