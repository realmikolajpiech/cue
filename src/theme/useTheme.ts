import { useAppearance } from './preferences';
const palettes = {
  light: { danger: '#B42318', background: '#F5F6FC', surface: '#FFFFFF', text: '#24283F', secondaryText: '#606982', accent: '#4563AB', onAccent: '#FFFFFF', border: '#DDE3F3', secondary: '#EBEEFB', mascotSurface: '#EFEDFF', cream: '#FFF9F4', warning: '#81480D', warningSoft: '#FCF2E5' },
  dark: { danger: '#FF8A80', background: '#171D32', surface: '#202840', text: '#F7F5FF', secondaryText: '#B9C2DD', accent: '#A7B8FF', onAccent: '#1B2442', border: '#36405F', secondary: '#2B3554', mascotSurface: '#2C3153', cream: '#202840', warning: '#EFB36C', warningSoft: '#3A2B1D' },
};
export function useTheme() { const isDark = useAppearance(s => s.dark); return { isDark, colors: palettes[isDark ? 'dark' : 'light'] }; }
export const spacing = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 } as const;
