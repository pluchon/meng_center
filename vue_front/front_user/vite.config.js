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
      // Element Plus 全局注册；保留同一包内的初始化顺序，避免拆成互相依赖的小块。
      chunkSizeWarningLimit: 1200,
      rolldownOptions: {
        output: {
          strictExecutionOrder: true,
          codeSplitting: {
            groups: [
              {
                name: 'element-plus',
                test: /node_modules[\\/](?:element-plus|@element-plus)[\\/]/,
                priority: 50,
              },
              {
                name: 'creator-charts',
                test: /node_modules[\\/](?:echarts|zrender)[\\/]/,
                priority: 40,
              },
              {
                name: 'aurora-ogl',
                test: /node_modules[\\/]ogl[\\/]/,
                priority: 40,
              },
              {
                name: 'liquid-three',
                test: /node_modules[\\/]three[\\/]/,
                maxSize: 300000,
                priority: 40,
              },
              {
                // 只有萌岛用，单独成块，别的页面不会下载
                name: 'island-babylon',
                test: /node_modules[\\/]@babylonjs[\\/]/,
                maxSize: 400000,
                priority: 40,
              },
              {
                name: 'codemirror',
                maxSize: 300000,
                test: /node_modules[\\/](?:@codemirror|codemirror|@lezer)[\\/]/,
                priority: 30,
              },
              {
                name: 'markdown',
                test: /node_modules[\\/](?:markdown-it|dompurify|highlight.js|linkify-it|uc\.micro|mdurl)[\\/]/,
                priority: 20,
              },
              { name: 'vendor', test: /node_modules/, maxSize: 300000, priority: 10 },
            ],
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
