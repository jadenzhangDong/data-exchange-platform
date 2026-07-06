import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
    plugins: [vue()],
    server: {
        proxy: {
            // 将 /api/master 代理到 Master 服务（端口 8080）
            '/api/master': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
            // 其他 /api 请求代理到 Web 服务（端口 8082）
            '/api': {
                target: 'http://localhost:8082',
                changeOrigin: true,
            }
        }
    },
    build: {
        outDir: '../dex-web/src/main/resources/static',
        emptyOutDir: true,
    }
})