import { router } from 'expo-router';
import { View } from 'react-native';
import { Screen, Copy, Card, Action, SectionHeading, Empty } from '@/components/ui';
import { useDemo, type Threat } from '@/features/demo/store';
import { threatCopy } from '@/features/demo/presentation';
import { useTheme } from '@/theme/useTheme';

function AlertCard({ threat }: { threat: Threat }) {
  const { colors } = useTheme();
  return <Card>
    <Copy title style={{ fontSize: 25, lineHeight: 35 }}>{threatCopy(threat).title}</Copy>
    <Copy>{threat.source} · {threat.time}</Copy>
    <Copy style={{ color: colors.text, fontFamily: 'DMSansMedium' }}>{threat.reviewed ? 'Przeczytane' : 'Nowe ostrzeżenie'}</Copy>
    <Action label="Co mam zrobić?" secondary={threat.reviewed} onPress={() => router.push(`/alert/${threat.id}`)} />
  </Card>;
}

export default function Alerts() {
  const threats = useDemo(s => s.threats);
  const unread = threats.filter(t => !t.reviewed);
  const read = threats.filter(t => t.reviewed);
  return <Screen title="Ostrzeżenia">
    <Copy>To przykładowe ostrzeżenia. Otwórz je, aby zobaczyć, co zrobić.</Copy>
    {!threats.length && <Empty title="Nie ma ostrzeżeń" subtitle="Nowe ostrzeżenia pojawią się tutaj." />}
    {!!unread.length && <View style={{ gap: 16 }}><SectionHeading title="Nowe" />{unread.map(t => <AlertCard key={t.id} threat={t} />)}</View>}
    {!!read.length && <View style={{ gap: 16 }}><SectionHeading title="Już przeczytane" />{read.map(t => <AlertCard key={t.id} threat={t} />)}</View>}
  </Screen>;
}
