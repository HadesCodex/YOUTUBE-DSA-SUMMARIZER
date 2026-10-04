import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// strictPort: the backend only allows CORS requests from http://localhost:5173,
// so fail loudly instead of silently moving to another port.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { port: 5173, strictPort: true },
  test: { environment: 'node' },
})
