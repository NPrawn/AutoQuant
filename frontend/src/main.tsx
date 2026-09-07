import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
// tokens 가 base 보다 먼저 와야 변수 정의가 사용처보다 앞선다.
import './styles/tokens.css'
import './styles/base.css'
import './styles/overrides.css'
import './styles/extensions.css'
import { router } from './app/router'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
)
