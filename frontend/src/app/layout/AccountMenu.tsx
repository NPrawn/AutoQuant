import { useEffect, useId, useRef, useState } from 'react'

import { LogoutConfirmModal } from './LogoutConfirmModal'

type AccountMenuProps = {
  displayName: string
  emailAddress: string
  /* 운용 중인 모의투자 수. 로그아웃 확인 모달이 계속 실행된다고 알린다. */
  runningSimulations: number
  onLogout?: () => void
}

export function AccountMenu({ displayName, emailAddress, runningSimulations, onLogout }: AccountMenuProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const avatarRef = useRef<HTMLButtonElement>(null)
  const menuId = useId()

  /* 메뉴 바깥을 누르거나 Esc 를 누르면 닫고, 키보드 사용자는 아바타로 포커스를 되돌린다. */
  useEffect(() => {
    if (!menuOpen) {
      return
    }
    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setMenuOpen(false)
      }
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMenuOpen(false)
        avatarRef.current?.focus()
      }
    }
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [menuOpen])

  const openConfirm = () => {
    setMenuOpen(false)
    setConfirmOpen(true)
  }

  const cancelConfirm = () => {
    setConfirmOpen(false)
    avatarRef.current?.focus()
  }

  const confirmLogout = () => {
    setConfirmOpen(false)
    onLogout?.()
  }

  return (
    <div className="acct" ref={rootRef}>
      <button
        ref={avatarRef}
        className="avatar"
        type="button"
        aria-haspopup="menu"
        aria-expanded={menuOpen}
        aria-controls={menuOpen ? menuId : undefined}
        onClick={() => setMenuOpen((current) => !current)}
      >
        {displayName}
      </button>

      {menuOpen && (
        <div className="acct-menu" id={menuId} role="menu" aria-label="계정 메뉴">
          <div className="acct-id">
            <b>{displayName}</b>
            <span>{emailAddress}</span>
          </div>
          {/* 아래 세 항목은 해당 화면이 생기기 전까지 비활성이다. */}
          <button className="acct-item" type="button" role="menuitem" disabled>
            계정 설정
          </button>
          <button className="acct-item" type="button" role="menuitem" disabled>
            거래소 연결<span>Upbit</span>
          </button>
          <button className="acct-item" type="button" role="menuitem" disabled>
            알림 설정
          </button>
          <div className="acct-sep" role="separator" />
          <button className="acct-item leave" type="button" role="menuitem" onClick={openConfirm}>
            로그아웃
          </button>
        </div>
      )}

      <LogoutConfirmModal open={confirmOpen} runningSimulations={runningSimulations} onCancel={cancelConfirm} onConfirm={confirmLogout} />
    </div>
  )
}
