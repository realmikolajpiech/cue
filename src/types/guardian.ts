import { z } from 'zod';

export const CURRENT_ANALYSIS_VERSION = 'guardian-pl-v3-evidence';
export const riskLabels = { low: 'Niskie ryzyko', medium: 'Umiarkowane ryzyko', high: 'Wysokie ryzyko', uncertain: 'Wynik niepewny' } as const;
export const categoryLabels = { family_impersonation: 'Podszywanie się pod bliską osobę', credential_theft: 'Wyłudzenie danych dostępu', payment_fraud: 'Podejrzana płatność', suspicious_link: 'Podejrzany link', manipulation: 'Możliwa manipulacja', unknown: 'Brak jednoznacznego wzorca' } as const;
export const signalLabels = { identity_change: 'Zmiana tożsamości lub numeru', urgency: 'Presja czasu', money_request: 'Prośba o pieniądze', credential_request: 'Prośba o hasło lub kod', suspicious_link: 'Podejrzany link', secrecy: 'Prośba o zachowanie tajemnicy', authority_claim: 'Powołanie się na autorytet', emotional_pressure: 'Presja emocjonalna' } as const;
export const resultSchema = z.object({
  analysisVersion: z.string().max(80).optional(), schemaVersion: z.literal(1), id: z.uuid(), createdAt: z.number().int().positive(),
  sourceApp: z.enum(['WhatsApp', 'Messenger', 'SMS', 'Beeper']),
  risk: z.enum(['low', 'medium', 'high', 'uncertain']), category: z.enum(['family_impersonation', 'credential_theft', 'payment_fraud', 'suspicious_link', 'manipulation', 'unknown']),
  signals: z.array(z.enum(['identity_change', 'urgency', 'money_request', 'credential_request', 'suspicious_link', 'secrecy', 'authority_claim', 'emotional_pressure'])).max(8),
  explanation: z.string().min(1).max(300), recommendedAction: z.string().min(1).max(400),
  analysisSource: z.literal('on_device'), reviewStatus: z.enum(['new', 'reviewed']),
}).strict();
export const statusSchema = z.object({
  benchmarkRunning: z.boolean(), benchmarkProgress: z.number().int().min(0).max(40),
  available: z.boolean(), notificationAccess: z.boolean(), listenerConnected: z.boolean(),
  monitoringEnabled: z.boolean(), modelState: z.enum(['missing', 'loading', 'ready', 'error']),
  backend: z.enum(['none', 'cpu', 'gpu']), processing: z.boolean(), error: z.string().nullable(), active: z.boolean(),
  modelInstalled: z.boolean(), modelSha256: z.string().nullable(), initializationMs: z.number(), promptVersion: z.string(), runtimeVersion: z.string(), notificationPermission: z.boolean(),
}).strict();
export const manualResultSchema = resultSchema.extend({ sourceApp: z.literal('Manual') });
export type ManualResult = z.infer<typeof manualResultSchema>;
export type GuardianResult = z.infer<typeof resultSchema>;
export type GuardianStatus = z.infer<typeof statusSchema>;
export const notificationSchema = z.object({
  id: z.string(), sourceApp: z.enum(['WhatsApp', 'Messenger', 'SMS', 'Beeper']),
  text: z.string().min(1).max(1500), createdAt: z.number().positive(),
});
export type GuardianNotification = z.infer<typeof notificationSchema>;
