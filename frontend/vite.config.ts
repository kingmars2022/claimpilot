import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// During development, API calls go through Vite to Spring Boot, so no CORS setup is needed.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
