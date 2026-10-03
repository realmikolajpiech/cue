import { PROMPT, MEMORY_PROMPT, NO_REPLY_PROMPT, REMINDERS_PROMPT } from './prompt.ts';
import { messageTime } from './message-time.ts';

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};
const reply = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status, headers: { ...cors, 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
});
const base = Deno.env.get('SUPABASE_URL')!;
const anon = Deno.env.get('SUPABASE_ANON_KEY')!;
const admin = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response(null, { headers: cors });
  if (req.method !== 'POST') return reply(405, { error: 'method_not_allowed' });
  try {
    const authorization = req.headers.get('Authorization');
    if (!authorization?.startsWith('Bearer ')) return reply(401, { error: 'unauthorized' });
    // Verify the session with Auth, never trust a decoded JWT or the public API key.
    const auth = await fetch(`${base}/auth/v1/user`, {
      headers: { apikey: anon, Authorization: authorization }, signal: AbortSignal.timeout(10000),
    });
    if (!auth.ok) return reply(auth.status >= 500 ? 503 : 401, { error: 'unauthorized' });
    const user = await auth.json();
    if (typeof user.id !== 'string') return reply(401, { error: 'unauthorized' });
    // Stream with a size cap even when Content-Length is absent.
    const reader = req.body?.getReader();
    if (!reader) return reply(400, { error: 'missing_body' });
    const chunks: Uint8Array[] = []; let size = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > 1500000) { await reader.cancel(); return reply(413, { error: 'body_too_large' }); }
      chunks.push(value);
    }
    const bytes = new Uint8Array(size); let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    let input;
    try { input = JSON.parse(new TextDecoder().decode(bytes)); }
    catch { return reply(400, { error: 'invalid_json' }); }
    if (!input || !Array.isArray(input.messages) || input.messages.length < 1 || input.messages.length > 80 ||
        typeof input.draft !== 'string' || input.draft.length > 4000) return reply(400, { error: 'invalid_input' });
    const ids = new Set<string>();
    for (const m of input.messages) {
      if (!m || typeof m.id !== 'string' || !m.id || m.id.length > 512 || ids.has(m.id) ||
          typeof m.sender !== 'string' || m.sender.length > 160 || typeof m.text !== 'string' || m.text.length > 4000 ||
          typeof m.isMe !== 'boolean' || typeof m.timestamp !== 'number' || !Number.isFinite(m.timestamp)) {
        return reply(400, { error: 'invalid_message' });
      }
      ids.add(m.id);
    }
    // Bound and validate the persistent memory independently of the recent chat context.
    const personMemory = input.personMemory;
    if (personMemory !== undefined && (!personMemory || typeof personMemory !== 'object' || Array.isArray(personMemory) ||
        JSON.stringify(personMemory).length > 120000)) return reply(400, { error: 'invalid_memory' });
    const styleInput = input.styleInput;
    if (styleInput !== undefined && (!styleInput || typeof styleInput !== 'object' || Array.isArray(styleInput) ||
        JSON.stringify(styleInput).length > 50000)) return reply(400, { error: 'invalid_style' });
    if (input.memoryOnly !== undefined && typeof input.memoryOnly !== 'boolean') return reply(400, { error: 'invalid_mode' });
    const key = Deno.env.get('DEEPSEEK_API_KEY');
    if (!key) return reply(503, { error: 'ai_not_configured' });
    const quota = await fetch(`${base}/rest/v1/rpc/cue_consume_ai_quota`, {
      method: 'POST', headers: { apikey: admin, Authorization: `Bearer ${admin}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ user_id: user.id }), signal: AbortSignal.timeout(10000),
    });
    if (!quota.ok) return reply(503, { error: 'quota_unavailable' });
    if (await quota.json() !== true) return reply(429, { error: 'daily_limit' });
    let timezone = typeof personMemory?.timezone === 'string' ? personMemory.timezone : 'UTC';
    try { new Intl.DateTimeFormat('en', { timeZone: timezone }).format(); } catch { timezone = 'UTC'; }
    const messages = input.messages.map((m: { id: string; sender: string; text: string; timestamp: number; isMe: boolean }) =>
      ({ id: m.id, sender: m.sender, text: m.text, timestamp: m.timestamp, isMe: m.isMe,
        calendar: messageTime(m.timestamp, timezone, m.text) }));
    const model = Deno.env.get('DEEPSEEK_MODEL') || 'deepseek-flash';
    const response = await fetch('https://api.deepseek.com/chat/completions', {
      method: 'POST', headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
      signal: AbortSignal.timeout(55000),
      body: JSON.stringify({ model, thinking: { type: 'disabled' }, response_format: { type: 'json_object' }, max_tokens: 4500,
        messages: [{ role: 'system', content: PROMPT + MEMORY_PROMPT + NO_REPLY_PROMPT + REMINDERS_PROMPT }, { role: 'user', content: JSON.stringify({ messages, draft: input.draft,
          personMemory, styleInput, memoryOnly: input.memoryOnly === true }) }] }),
    });
    if (!response.ok) return reply([402, 429].includes(response.status) ? response.status : 502, { error: 'upstream_unavailable' });
    const completion = await response.json();
    const choice = completion.choices?.[0];
    if (choice?.finish_reason !== 'stop') return reply(502, { error: 'incomplete_response' });
    const profile = JSON.parse(choice.message.content);
    // Native client validates structure and evidence again before saving locally.
    if (!profile || typeof profile.summary !== 'string' || typeof profile.beforeReply !== 'string' ||
        !Array.isArray(profile.observations) || !Array.isArray(profile.commitments) ||
        !Array.isArray(profile.suggestions) || (!input.memoryOnly && !profile.suggestions.length) ||
        (personMemory !== undefined && !Array.isArray(profile.memoryUpdates)) ||
        (personMemory?.reminders !== undefined && !Array.isArray(profile.reminderUpdates))) return reply(502, { error: 'invalid_response' });
    return reply(200, profile);
  } catch {
    // Never log request bodies, provider responses, session tokens or credentials.
    return reply(502, { error: 'analysis_unavailable' });
  }
});
