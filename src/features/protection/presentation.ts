import type { Threat } from '@/features/demo/store';
import { CURRENT_ANALYSIS_VERSION, categoryLabels, riskLabels, signalLabels, type GuardianResult } from '@/types/guardian';

export function isWarning(threat: Threat) {
  return threat.risk === riskLabels.high || threat.risk === riskLabels.medium || threat.risk === 'Średnie ryzyko';
}

export function analysisTitle(result: Pick<GuardianResult, 'analysisVersion' | 'risk' | 'category'>) {
  if (result.analysisVersion !== CURRENT_ANALYSIS_VERSION) return 'Wynik wymaga ponownej analizy';
  if (result.risk === 'low') return 'Nie wykryto zagrożenia';
  if (result.risk === 'uncertain') return 'Nie udało się ocenić ryzyka';
  return categoryLabels[result.category];
}

export function toThreat(result: GuardianResult): Threat {
  const current = result.analysisVersion === CURRENT_ANALYSIS_VERSION;
  return {
    id: result.id, title: analysisTitle(result), source: result.sourceApp,
    time: new Date(result.notificationContext?.receivedAt ?? result.createdAt).toLocaleString('pl-PL', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }),
    risk: riskLabels[current ? result.risk : 'uncertain'],
    signals: current ? result.signals.map(signal => signalLabels[signal]) : [],
    advice: current ? result.recommendedAction : 'Sprawdź wiadomość ponownie w zakładce „Sprawdź”.',
    reviewed: result.reviewStatus === 'reviewed', notificationContext: result.notificationContext,
  };
}
