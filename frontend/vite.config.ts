import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // .env 파일의 값은 process.env 에 자동으로 올라오지 않으므로 loadEnv 로 읽는다.
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        // API 가 두 개라 프론트에서 CORS 를 두 번 겪게 된다.
        // 개발 중에는 프록시로 같은 오리진으로 받아 CORS 를 아예 만들지 않는다.
        '/api/market': {
          target: env.VITE_API_MARKET_URL || 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api\/market/, ''),
        },
        '/api/trading': {
          target: env.VITE_API_TRADING_URL || 'http://localhost:8081',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api\/trading/, ''),
        },
      },
    },
  }
})
