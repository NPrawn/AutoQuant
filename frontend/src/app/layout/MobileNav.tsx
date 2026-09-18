import { useEffect, useId, useRef, useState, type MouseEvent } from 'react'
import { NavLink } from 'react-router'

import { navSections } from '../navigation'

/*
 * 960px 이하에서 사이드바가 사라진 자리의 메뉴. 상단바의 버튼이 열고, 항목은 사이드바와 같은
 * navigation.tsx 를 그대로 쓴다. 겉모습은 목업의 .mbd·.modal 이고 포커스 트랩과 Esc 는 dialog 가 맡는다.
 * 버튼 자체는 extensions.css 가 960px 이하에서만 보인다.
 */
export function MobileNav() {
  const [open, setOpen] = useState(false)
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

  const handleBackdropClick = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === dialogRef.current) {
      setOpen(false)
    }
  }

  return (
    <>
      <button className="icon-btn nav-toggle" type="button" aria-label="메뉴 열기" aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen(true)}>
        <svg viewBox="0 0 24 24" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M4 7h16M4 12h16M4 17h16" />
        </svg>
      </button>
      <dialog ref={dialogRef} className="mbd on nav-sheet-scrim" aria-labelledby={titleId} onClose={() => setOpen(false)} onClick={handleBackdropClick}>
        <div className="modal nav-sheet">
          <div className="nav-sheet-head">
            <h3 id={titleId}>메뉴</h3>
            <button className="icon-btn" type="button" aria-label="메뉴 닫기" onClick={() => setOpen(false)}>
              <svg viewBox="0 0 24 24" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </div>
          {navSections.map((section) => (
            <nav key={section.label} className="navsec" aria-label={section.label}>
              <span className="lbl">{section.label}</span>
              {section.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) => (isActive ? 'navbtn active' : 'navbtn')}
                  onClick={() => setOpen(false)}
                >
                  <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    {item.icon}
                  </svg>
                  {item.label}
                </NavLink>
              ))}
            </nav>
          ))}
        </div>
      </dialog>
    </>
  )
}
