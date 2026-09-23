import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'

import { StrategyName } from '../strategy/StrategyName'
import type { Strategy } from '../strategy/types'

type StrategySelectProps = {
  id: string
  strategies: Strategy[]
  value: string
  disabled?: boolean
  onChange: (strategyId: string) => void
}

/*
 * 전략을 고르는 드롭다운. 브라우저 select 는 옵션 안에 글자만 둘 수 있어 버전이 이름에 붙어 보였다.
 * 버튼과 리스트박스로 그려 이름은 왼쪽, 버전은 오른쪽 끝에 흐린 글자로 둔다.
 * 리스트는 열렸을 때만 렌더링하고 바깥 pointerdown 과 Esc 로 닫으며, 닫히면 버튼으로 포커스를 되돌린다.
 */
export function StrategySelect({ id, strategies, value, disabled = false, onChange }: StrategySelectProps) {
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const rootRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const listId = useId()

  const selected = strategies.find((strategy) => strategy.id === value) ?? null
  const selectedIndex = Math.max(strategies.findIndex((strategy) => strategy.id === value), 0)

  const close = () => {
    setOpen(false)
    buttonRef.current?.focus()
  }

  const show = () => {
    setActive(selectedIndex)
    setOpen(true)
  }

  const pick = (strategyId: string) => {
    if (strategyId !== value) {
      onChange(strategyId)
    }
    close()
  }

  useEffect(() => {
    if (!open) {
      return
    }
    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', handlePointerDown)
    return () => document.removeEventListener('pointerdown', handlePointerDown)
  }, [open])

  /* 활성 항목이 리스트 밖으로 나가면 보이게 끌어온다. */
  useEffect(() => {
    if (open) {
      document.getElementById(`${listId}-${active}`)?.scrollIntoView({ block: 'nearest' })
    }
  }, [open, active, listId])

  const handleKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (!open) {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp' || event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        show()
      }
      return
    }
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault()
        setActive((current) => Math.min(current + 1, strategies.length - 1))
        break
      case 'ArrowUp':
        event.preventDefault()
        setActive((current) => Math.max(current - 1, 0))
        break
      case 'Home':
        event.preventDefault()
        setActive(0)
        break
      case 'End':
        event.preventDefault()
        setActive(strategies.length - 1)
        break
      case 'Enter':
      case ' ':
        event.preventDefault()
        if (strategies[active] !== undefined) {
          pick(strategies[active].id)
        }
        break
      case 'Escape':
        event.preventDefault()
        close()
        break
      case 'Tab':
        setOpen(false)
        break
      default:
        break
    }
  }

  return (
    <div className="strategy-select" ref={rootRef} onKeyDown={handleKeyDown}>
      <button
        id={id}
        ref={buttonRef}
        className="strategy-select-btn"
        type="button"
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-activedescendant={open ? `${listId}-${active}` : undefined}
        disabled={disabled}
        onClick={() => (open ? close() : show())}
      >
        <span className="strategy-select-label">
          {selected === null ? '전략을 선택해 주세요' : <StrategyName label={selected} />}
        </span>
        <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d={open ? 'M6 15l6-6 6 6' : 'M6 9l6 6 6-6'} />
        </svg>
      </button>

      {open && (
        <ul className="strategy-select-list" id={listId} role="listbox" aria-labelledby={id}>
          {strategies.map((strategy, index) => (
            <li
              key={strategy.id}
              id={`${listId}-${index}`}
              className={index === active ? 'strategy-option active' : 'strategy-option'}
              role="option"
              aria-selected={strategy.id === value}
              onPointerMove={() => setActive(index)}
              onClick={() => pick(strategy.id)}
            >
              <StrategyName label={strategy} />
              {strategy.id === value && (
                <svg viewBox="0 0 24 24" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M5 12l5 5L20 7" />
                </svg>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
