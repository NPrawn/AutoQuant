import type { FormEvent, KeyboardEvent } from 'react'
import { useEffect, useRef, useState } from 'react'

import { translateInputMaxLength } from '../types'
import type { AssistantMessage, Conversation } from './conversation'

type AssistantPanelProps = {
  conversation: Conversation
  /* 블록 상태를 읽어 준 첫 말풍선. 대화에 저장하지 않아 블록이 바뀌면 함께 바뀐다. */
  opening: string
  suggestions: readonly string[]
  onSend: (text: string) => void
}

/*
 * 목업 19 의 전략봇. 오른쪽 아래 버튼으로 열고 닫는다. 패널은 dialog 역할이지만 화면을
 * 막지 않는 보조 창이라 showModal 대신 열림 상태만 둔다. Esc 는 닫기, Enter 는 보내기다.
 */
export function AssistantPanel({ conversation, opening, suggestions, onSend }: AssistantPanelProps) {
  const [open, setOpen] = useState(false)
  const [input, setInput] = useState('')
  const bodyRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const fabRef = useRef<HTMLButtonElement>(null)

  /* 새 메시지나 생각 중 표시가 붙으면 맨 아래가 보이게 한다. */
  useEffect(() => {
    const body = bodyRef.current
    if (body) {
      body.scrollTop = body.scrollHeight
    }
  }, [conversation.messages.length, conversation.pending, open])

  /*
   * 열리면 입력창으로, 닫히면 여는 버튼으로 포커스를 옮긴다. 버튼은 닫힌 뒤에야 다시
   * 렌더링되므로 닫는 순간이 아니라 그 다음 렌더에서 포커스를 준다.
   */
  const refocusFab = useRef(false)
  useEffect(() => {
    if (open) {
      inputRef.current?.focus()
    } else if (refocusFab.current) {
      refocusFab.current = false
      fabRef.current?.focus()
    }
  }, [open])

  const close = () => {
    refocusFab.current = true
    setOpen(false)
  }

  const send = (text: string) => {
    const trimmed = text.trim()
    if (trimmed === '' || conversation.pending) {
      return
    }
    onSend(trimmed)
    setInput('')
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    send(input)
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.stopPropagation()
      close()
    }
  }

  return (
    <>
      {!open && (
        <button ref={fabRef} className="bot-fab" type="button" aria-label="전략봇 열기" aria-expanded={false} onClick={() => setOpen(true)}>
          <i aria-hidden="true">AI</i>전략봇
        </button>
      )}

      {open && (
        <div className="bot-panel on" role="dialog" aria-label="전략봇" onKeyDown={handleKeyDown}>
          <div className="bot-head">
            <i aria-hidden="true">AI</i>
            <div>
              <b>전략봇</b>
              <small>이 화면의 전략을 보고 답합니다</small>
            </div>
            <button className="bot-x" type="button" aria-label="닫기" onClick={close}>
              ×
            </button>
          </div>

          <div ref={bodyRef} className="bot-body" role="log" aria-live="polite" aria-relevant="additions">
            <div className="bot-msg bot">{opening}</div>
            {conversation.messages.map((message) => (
              <MessageBubble key={message.id} message={message} />
            ))}
            {conversation.pending && (
              <div className="bot-msg bot thinking" aria-label="답변 작성 중">
                <i /><i /><i />
              </div>
            )}
          </div>

          <div className="bot-chips">
            {suggestions.map((suggestion) => (
              <button key={suggestion} type="button" onClick={() => setInput(suggestion)}>
                {suggestion}
              </button>
            ))}
          </div>

          <form className="bot-foot" onSubmit={handleSubmit}>
            <input
              ref={inputRef}
              type="text"
              placeholder="전략에 대해 물어보세요"
              aria-label="전략봇에게 보낼 문장"
              maxLength={translateInputMaxLength}
              value={input}
              onChange={(event) => setInput(event.target.value)}
            />
            <button type="submit" aria-label="보내기" disabled={conversation.pending || input.trim() === ''}>
              ↑
            </button>
          </form>
        </div>
      )}
    </>
  )
}

function MessageBubble({ message }: { message: AssistantMessage }) {
  if (message.role === 'user') {
    return <div className="bot-msg me">{message.text}</div>
  }
  return (
    <div className="bot-msg bot">
      {message.text}
      {message.notes && (
        <ul className="bot-notes">
          {message.notes.changes.map((line) => (
            <li key={`c-${line}`} className="change">{line}</li>
          ))}
          {message.notes.assumptions.map((line) => (
            <li key={`a-${line}`} className="assumption">{line}</li>
          ))}
          {message.notes.warnings.map((line) => (
            <li key={`w-${line}`} className="warning">{line}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
