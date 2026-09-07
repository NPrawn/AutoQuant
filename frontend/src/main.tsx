import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// tokens 가 base 보다 먼저 와야 변수 정의가 사용처보다 앞선다.
import './styles/tokens.css'
import './styles/base.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
