import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
    './src/content/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Core Electric Fluid Tokens
        fluid: {
          base: '#070B14',
          surface: '#0D1527',
          surfaceElevated: '#131D35',
          border: 'rgba(255, 255, 255, 0.08)',
          borderHover: 'rgba(6, 182, 212, 0.3)',
          cobalt: '#2563EB',
          violet: '#7C3AED',
          cyan: '#06B6D4',
          coral: '#F43F5E',
          amber: '#F59E0B',
          text: '#F8FAFC',
          muted: '#94A3B8',
        },
        navy: {
          950: '#070B14',
          900: '#0D1527',
          800: '#131D35',
          700: '#1E293B',
        },
      },
      fontFamily: {
        sans: ['"Plus Jakarta Sans"', 'Inter', 'system-ui', '-apple-system', 'sans-serif'],
        display: ['"Sora"', '"Plus Jakarta Sans"', 'sans-serif'],
        sora: ['"Sora"', 'sans-serif'],
        jakarta: ['"Plus Jakarta Sans"', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', 'monospace'],
      },
      borderRadius: {
        input: '8px',
        card: '16px',
        panel: '24px',
      },
      boxShadow: {
        'glow-cobalt': '0 0 35px -5px rgba(37, 99, 235, 0.35)',
        'glow-violet': '0 0 35px -5px rgba(124, 58, 237, 0.35)',
        'glow-cyan': '0 0 35px -5px rgba(6, 182, 212, 0.35)',
        'glow-coral': '0 0 35px -5px rgba(244, 63, 94, 0.4)',
      },
    },
  },
  plugins: [],
};

export default config;
