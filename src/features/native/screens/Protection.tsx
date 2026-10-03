import { Switch } from 'react-native';
import * as Linking from 'expo-linking';
import * as DocumentPicker from 'expo-document-picker';
import { Screen, Copy, Card, Action, InlineError } from '@/components/ui';
import { guardian } from '@/services/guardian';
import { useAction, useGuardianStatus } from '@/services/queries';

export default function Protection() {
  const status = useGuardianStatus(); const action = useAction(); const s = status.data;
  async function importModel() {
    const file = await DocumentPicker.getDocumentAsync({ copyToCacheDirectory: false, multiple: false });
    if (file.canceled) return;
    if (!file.assets[0].name.endsWith('.litertlm')) throw new Error('invalid_format');
    await guardian.importModel(file.assets[0].uri);
  }
  return <Screen>
    <Copy title>{s?.active ? 'Ochrona jest aktywna' : 'Skonfiguruj ochronę'}</Copy>
    <Copy>{s?.active ? 'Analiza działa lokalnie. Brak ostrzeżenia nie gwarantuje bezpieczeństwa.' : 'Guardian potrzebuje lokalnego modelu i dostępu do powiadomień.'}</Copy>
    {status.isPending && <Copy>Sprawdzam stan urządzenia…</Copy>}
    <InlineError message={status.isError ? 'Nie można odczytać stanu ochrony.' : action.error} />
    {status.isError && <Action label="Spróbuj ponownie" onPress={() => { void status.refetch(); }} />}
    {s && !s.available && <Card><Copy title>Wymagany Android</Copy><Copy>Uruchom własny build Guardian na Androidzie. Expo Go, iOS i web nie obsługują odczytu powiadomień innych aplikacji.</Copy></Card>}
    <Card><Copy title>1. Model na urządzeniu</Copy>
      <Copy>{s?.modelState === 'ready' ? `Model gotowy · ${s.backend.toUpperCase()}` : s?.modelState === 'loading' ? 'Ładowanie modelu…' : s?.modelState === 'error' ? 'Model nie uruchomił się. Sprawdź zgodność pliku .litertlm.' : s?.modelInstalled ? 'Model zainstalowany. Włącz monitorowanie, aby go uruchomić.' : 'Nie zaimportowano modelu.'}</Copy>
      <Copy>Gemma 3 1B INT4 · 584 MB. Pobierz gemma3-1b-it-int4.litertlm po zaakceptowaniu licencji na Hugging Face. Guardian sprawdza plik przed instalacją. Import nie włącza monitorowania.</Copy>
      <Action label="Pobierz Gemma na Hugging Face" secondary disabled={action.busy} onPress={() => { void action.run(() => Linking.openURL('https://huggingface.co/litert-community/Gemma3-1B-IT/blob/a6306a4e292016480083b73b8dc6f3f939ae04c3/gemma3-1b-it-int4.litertlm')); }} />
      <Action label={action.busy ? 'Trwa operacja…' : 'Importuj model .litertlm'} disabled={!s?.available || action.busy} onPress={() => { void action.run(importModel); }} />
    </Card>
    <Card><Copy title>2. Dostęp do powiadomień</Copy><Copy>Po włączeniu dostępu Android udostępni Guardian treść powiadomień. Analizowane są tylko WhatsApp, Messenger oraz SMS z Google i Samsung. Wiadomości są przetwarzane lokalnie, a kontekst znika z RAM po 15 minutach.</Copy>
      <Copy>{s?.notificationAccess ? s.listenerConnected ? 'Listener jest połączony.' : 'Zgoda udzielona. Oczekiwanie na połączenie…' : 'Dostęp nie jest włączony.'}</Copy>
      <Action label="Otwórz ustawienia dostępu" secondary disabled={!s?.available || action.busy} onPress={() => { void action.run(guardian.openSettings); }} />
    </Card>
    <Card><Copy title>3. Ostrzeżenia systemowe</Copy><Copy>{s?.notificationPermission ? 'Powiadomienia są dozwolone.' : 'Zezwól na ostrzeżenia, aby widzieć wysokie ryzyko poza aplikacją.'}</Copy>
      <Action label="Zezwól na ostrzeżenia" secondary disabled={!s?.available} onPress={guardian.warningPermission} />
    </Card>
    <Card><Copy title>Monitorowanie</Copy><Copy>Przełącznik włącza analizę nowych powiadomień. Wyłączenie czyści kontekst i zatrzymuje bieżącą analizę.</Copy>
      <Switch accessibilityLabel="Monitorowanie powiadomień" value={s?.monitoringEnabled ?? false} disabled={!s?.available || action.busy || (!s.monitoringEnabled && (!s.modelInstalled || !s.notificationAccess))} onValueChange={value => { void action.run(() => guardian.monitor(value)); }} />
      {s?.processing && <Copy>Analizuję kontekst…</Copy>}
      {s?.error && <Copy>Ostatnia operacja silnika nie powiodła się. Sprawdź model i uruchom ochronę ponownie.</Copy>}
    </Card>
  </Screen>;
}
