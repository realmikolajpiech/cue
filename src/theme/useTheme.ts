import { t } from '@/i18n';
import { useDemo } from '@/features/demo/store';
const palettes = {
  light: { background: '#F7F7F7', surface: '#FFFFFF', text: '#202020', secondaryText: '#505050', accent: '#202020', border: '#E7E7E7', secondary: '#F3F3F3', warning: '#81480D', warningSoft: '#FCF2E5' },
  dark: { background: '#111111', surface: '#1B1B1B', text: '#F5F5F5', secondaryText: '#C2C2C2', accent: '#F5F5F5', border: '#303030', secondary: '#252525', warning: '#EFB36C', warningSoft: '#3A2B1D' },
};
export function useTheme() { const isDark = useDemo(s => s.dark); return { isDark, colors: palettes[isDark ? 'dark' : 'light'] }; }
export const spacing = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 } as const;

export type RiskLevel = 'high' | 'medium' | 'low' | 'uncertain';
const riskPalettes = {
  light: {
    high: { foreground: '#B42318', background: '#FEE4E2' },
    medium: { foreground: '#B54708', background: '#FFF0D6' },
    low: { foreground: '#856300', background: '#FFF5C2' },
    uncertain: { foreground: '#667085', background: '#EAECF0' },
  },
  dark: {
    high: { foreground: '#FF8A80', background: '#3B1C1A' },
    medium: { foreground: '#FFBA66', background: '#3C2A15' },
    low: { foreground: '#EBD34E', background: '#332E12' },
    uncertain: { foreground: '#B0B8C5', background: '#282D35' },
  },
};
export function riskFromLabel(label: string): RiskLevel {
  if ((label === 'Wysokie ryzyko' || label === t('Wysokie ryzyko'))) return 'high';
  if ((label === 'Umiarkowane ryzyko' || label === t('Umiarkowane ryzyko')) || (label === 'Średnie ryzyko' || label === t('Średnie ryzyko'))) return 'medium';
  if ((label === 'Niskie ryzyko' || label === t('Niskie ryzyko'))) return 'low';
  return 'uncertain';
}
export function riskColors(risk: RiskLevel, dark: boolean) { return riskPalettes[dark ? 'dark' : 'light'][risk]; }
