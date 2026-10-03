import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        marinha: '#0A2463',
        medio: '#1E5AA8',
        claro: '#EAF3FC',
        tinta: '#33415C',
        ouro: '#C9A227',
        fase1: '#2D7DD2',
        fase2: '#C9A227',
        fase3: '#2E7D5B',
        fase4: '#5A6B85',
        exercito: '#4B5320',
      },
    },
  },
  plugins: [],
} satisfies Config;
