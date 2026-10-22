import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
export default defineConfig({
  base: '/frontend/',
  plugins: [react()],
  build: {
    outDir: '../src/main/resources/static/frontend',
    emptyOutDir: true,
    rollupOptions: { output: { entryFileNames: 'index.js', chunkFileNames: '[name].js', assetFileNames: '[name][extname]' } },
  },
  server: { proxy: { '/api': 'http://127.0.0.1:8080' } },
})
