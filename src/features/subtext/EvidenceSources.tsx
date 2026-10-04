import { View } from 'react-native';
import { Copy } from '@/components/ui';
import { useRoom } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import type { Room } from '@/types/subtext';
import { Disclosure, ui } from './components';

export default function EvidenceSources({ roomId, evidenceIds, sources = [], manualAt }: {
  roomId: string; evidenceIds: string[]; sources?: NonNullable<Room['messages']>; manualAt?: number;
}) {
  const { data: room } = useRoom(roomId);
  const { colors } = useTheme();
  const messages = new Map([...(room?.messages ?? []), ...sources].map(message => [message.id, message]));
  return <Disclosure label={`Pokaż źródło (${evidenceIds.length})`}>
    <Copy style={ui.small}>To wiadomości wskazane przez AI. Sprawdź, czy rzeczywiście potwierdzają ustalenie.</Copy>
    {!!manualAt && <Copy style={ui.small}>Wpis zmieniony ręcznie. Źródła dotyczą wcześniejszej analizy, nie potwierdzają Twojej korekty.</Copy>}
    {evidenceIds.map(id => {
      const source = messages.get(id);
      return <View key={id} style={{ borderLeftWidth: 2, borderColor: colors.accent, paddingLeft: 12, gap: 4 }}>
        {source ? <>
          <Copy style={ui.small}>{source.isMe ? 'Ty' : source.sender || room?.name} · {new Date(source.timestamp).toLocaleString('pl-PL')}</Copy>
          <Copy selectable style={[ui.body, { color: colors.text }]}>{source.text}</Copy>
        </> : <Copy style={ui.small}>Źródłowa wiadomość nie jest już dostępna. Ten starszy wpis nie zawiera zachowanego cytatu.</Copy>}
      </View>;
    })}
    {!evidenceIds.length && <Copy style={ui.small}>Brak zapisanych źródeł. Zweryfikuj wpis przed użyciem.</Copy>}
  </Disclosure>;
}
