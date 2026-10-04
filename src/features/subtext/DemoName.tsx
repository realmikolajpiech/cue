import { useState } from 'react';
import { View } from 'react-native';
import { usePreferences } from '@/features/preferences';
import { subtextCache } from '@/services/subtext';
import type { Room } from '@/types/subtext';
import { useTranslation } from '@/i18n';
import { Button, Disclosure, Field } from './components';

/** Changes only the name shown in demo mode; the conversation keeps its real name. */
export default function DemoName({ id }: { id: string }) {
  const { t } = useTranslation();
  const saved = usePreferences(s => s.demoNames[id]); const setName = usePreferences(s => s.setDemoName);
  const original = subtextCache.getQueryData<Room>(['subtext', 'room', id])?.name;
  const [draft, setDraft] = useState(saved ?? '');
  return <Disclosure label={t('profile.demoName')} small>
    <Field value={draft} onChangeText={setDraft} maxLength={60} autoCapitalize="words" returnKeyType="done"
      accessibilityLabel={t('profile.demoName')} placeholder={original} onSubmitEditing={() => setName(id, draft)} />
    <View style={{ gap: 8 }}>
      <Button label={t('common.save')} disabled={draft.trim() === (saved ?? '')} onPress={() => setName(id, draft)} />
      {!!saved && <Button label={t('profile.demoNameReset')} secondary onPress={() => { setDraft(''); setName(id, ''); }} />}
    </View>
  </Disclosure>;
}
