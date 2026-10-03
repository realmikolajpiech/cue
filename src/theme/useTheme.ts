import { useDemo } from '@/features/demo/store';
const palettes = {
  light: { background: '#F7F7F7', surface: '#FFFFFF', text: '#202020', secondaryText: '#505050', accent: '#202020', border: '#E7E7E7', secondary: '#F3F3F3', warning: '#81480D', warningSoft: '#FCF2E5' },
  dark: { background: '#111111', surface: '#1B1B1B', text: '#F5F5F5', secondaryText: '#C2C2C2', accent: '#F5F5F5', border: '#303030', secondary: '#252525', warning: '#EFB36C', warningSoft: '#3A2B1D' },
};
export function useTheme() { const isDark = useDemo(s => s.dark); return { isDark, colors: palettes[isDark ? 'dark' : 'light'] }; }
export const spacing = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 } as const;
