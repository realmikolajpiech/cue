import { z } from 'zod';

export const networkSchema = z.enum(['messenger', 'whatsapp']);
export type Network = z.infer<typeof networkSchema>;
export const messageSchema = z.object({ id: z.string(), sender: z.string(), text: z.string(), timestamp: z.number(), isMe: z.boolean() });
const evidenceSchema = z.object({ text: z.string(), evidenceIds: z.array(z.string()) });
export const profileSchema = z.object({
  summary: z.string(), beforeReply: z.string(), observations: z.array(evidenceSchema), commitments: z.array(evidenceSchema),
  suggestions: z.array(z.object({ tone: z.string(), text: z.string() })), createdAt: z.number(), model: z.string(), messageCount: z.number(),
});
export const roomSchema = z.object({
  id: z.string(), remoteId: z.string(), network: networkSchema, name: z.string(), kind: z.string(),
  updatedAt: z.number(), snippet: z.string(), profile: profileSchema.nullable(), messageCount: z.number().optional(),
  historyNotice: z.string().optional(), messages: z.array(messageSchema).optional(), demo: z.boolean().optional(),
});
const connectionSchema = z.object({ phase: z.string(), detail: z.string(), pairingCode: z.string().nullable().optional() });
export const subtextStatusSchema = z.object({
  available: z.boolean(), hasApiKey: z.boolean(), cloudEnabled: z.boolean(), backgroundEnabled: z.boolean(),
  model: z.string(), analyzing: z.string().nullable(), messenger: connectionSchema, whatsapp: connectionSchema,
});
export type Room = z.infer<typeof roomSchema>;
export type Profile = z.infer<typeof profileSchema>;

export const writingStyleSchema = z.object({
  generated: z.boolean().optional(),
  updatedAt: z.number().optional(), contextUpdatedAt: z.number().optional(), pendingMessages: z.number().optional(), contextError: z.string().optional(),
  traits: z.array(z.object({ text: z.string(), matches: z.number(), sampleSize: z.number() })).optional(),
  phrases: z.array(z.object({ text: z.string(), count: z.number() })).optional(),
  relationship: z.array(z.object({ id: z.string(), text: z.string(), updatedAt: z.number(), evidenceIds: z.array(z.string()) })).optional(),
  previewExamples: z.array(z.object({ id: z.string(), incoming: z.string(), reply: z.string(), timestamp: z.number() })).optional(),
  sampleCount: z.number(), conversationCount: z.number(), summary: z.string(), habits: z.array(z.string()),
  examples: z.array(z.object({ id: z.string(), incoming: z.string(), reply: z.string(), timestamp: z.number() })),
});
