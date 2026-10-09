import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { imagetools } from 'vite-imagetools'
import { fileURLToPath, URL } from 'node:url'
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    // imagetools 按引用地址里的参数在构建时缩放、转码图片
    plugins: [vue(), imagetools()],
    build: {
      rolldownOptions: {
        output: {
          codeSplitting: {
            groups: [{ name: 'vendor', test: /node_modules/, maxSize: 300000, priority: 10 }],
          },
        },
      },
    },
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      host: '127.0.0.1',
      port: Number(env.VITE_DEV_PORT),
      strictPort: true,
      proxy: {
        '/api': {
          target: env.VITE_GATEWAY_TARGET,
          changeOrigin: false,
          ws: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
    css: {
      preprocessorOptions: { scss: { additionalData: '@use "@/styles/_mixins.scss" as *;' } },
    },
  }
})
