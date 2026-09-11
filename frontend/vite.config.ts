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
        '/ws': {
          target: env.VITE_API_URL || 'http://localhost:8080',
          ws: true,
          changeOrigin: false,
        },
        // 개발 중에는 프록시로 같은 오리진에서 받아 CORS 를 만들지 않는다. 모든 API 가 같은 규칙으로 접두사를 뗀다.
        '/api': {
          target: env.VITE_API_URL || 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
  }
})
