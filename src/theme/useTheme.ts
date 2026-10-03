import { useColorScheme } from 'react-native';

const palettes = {
  light: {
    background: '#F5F7F6',
    surface: '#FFFFFF',
    text: '#17231C',
    secondaryText: '#526258',
    accent: '#246B47',
    border: '#DCE4DF',
  },
  dark: {
    background: '#111814',
    surface: '#1B2520',
    text: '#ECF3EF',
    secondaryText: '#ACBEB2',
    accent: '#8ED0A9',
    border: '#33463A',
  },
} as const;

export function useTheme() {
  const isDark = useColorScheme() === 'dark';
  return { isDark, colors: palettes[isDark ? 'dark' : 'light'] };
}

export const spacing = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 } as const;
