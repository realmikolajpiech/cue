import type { Network, Room } from '@/types/subtext';
import { dateLocale, t } from '@/i18n';

export type InboxFilter = 'all' | Network;
export const networkName = (network: Network) => network === 'messenger' ? 'Messenger' : 'WhatsApp';
export function normalizeSearch(value: string) {
  return value.toLocaleLowerCase('pl-PL').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/ł/g, 'l').trim();
}
export function filterConversations(rooms: Room[], search: string, network: InboxFilter) {
  const words = normalizeSearch(search).split(/\s+/).filter(Boolean);
  return rooms.filter(room => (network === 'all' || room.network === network) &&
    words.every(word => normalizeSearch(room.name).includes(word)))
    .sort((a, b) => b.updatedAt - a.updatedAt || a.name.localeCompare(b.name, 'pl'));
}
export function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return (parts.length > 1 ? `${Array.from(parts[0])[0]}${Array.from(parts[parts.length - 1])[0]}` : Array.from(parts[0] ?? '?').slice(0, 2).join('')).toLocaleUpperCase('pl');
}
export function conversationTime(timestamp: number, now = new Date()) {
  if (!timestamp || !Number.isFinite(timestamp)) return '';
  const date = new Date(timestamp);
  if (date.toDateString() === now.toDateString()) return date.toLocaleTimeString(dateLocale(), { hour: '2-digit', minute: '2-digit' });
  const yesterday = new Date(now); yesterday.setDate(yesterday.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) return t('inbox.yesterday');
  return date.toLocaleDateString(dateLocale(), { day: 'numeric', month: 'short', ...(date.getFullYear() !== now.getFullYear() ? { year: 'numeric' } : {}) });
}

export function messageText(text: string) {
  if (!text.startsWith('[Zdjęcie]')) return text;
  const caption = text.slice('[Zdjęcie]'.length).trim();
  return caption ? t('inbox.photoSentWithCaption', { caption }) : t('inbox.photoSent');
}
