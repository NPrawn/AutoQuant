import type { MouseEvent } from 'react'
import { useEffect, useRef } from 'react'

/*
 * 네이티브 dialog 를 showModal 로 열어 포커스 트랩, Esc 닫기, 바깥 요소 비활성화를
 * 브라우저에 맡긴다. 겉모습은 목업의 .mbd 스크림과 .modal 카드를 그대로 쓴다.
 * 목업의 "저장되지 않은 편집" 행은 그 상태를 아는 곳이 없어 두지 않는다.
 */
type LogoutConfirmModalProps = {
  open: boolean
  runningSimulations: number
  onCancel: () => void
  onConfirm: () => void
}

export function LogoutConfirmModal({ open, runningSimulations, onCancel, onConfirm }: LogoutConfirmModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

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
    if (event.target === dialogRef.current) {
      onCancel()
    }
  }

  const description =
    runningSimulations > 0
      ? `운용 중인 모의투자 ${runningSimulations}건은 로그아웃 후에도 서버에서 계속 실행됩니다.`
      : '로그아웃해도 저장한 전략과 기록은 유지됩니다.'

  return (
    <dialog
      ref={dialogRef}
      className="mbd on"
      aria-labelledby="logout-confirm-title"
      onCancel={onCancel}
      onClose={onCancel}
      onClick={handleBackdropClick}
    >
      <div className="modal">
        <h3 id="logout-confirm-title">로그아웃할까요?</h3>
        <p className="s">{description}</p>
        {runningSimulations > 0 && (
          <div className="state-detail" style={{ width: '100%', margin: '0 0 20px' }}>
            <div>
              <span>운용 중 모의투자</span>
              <strong>{runningSimulations}건</strong>
            </div>
          </div>
        )}
        <div className="macts">
          <button className="btn" type="button" onClick={onCancel}>
            취소
          </button>
          <button className="btn primary" type="button" onClick={onConfirm}>
            로그아웃
          </button>
        </div>
      </div>
    </dialog>
  )
}
